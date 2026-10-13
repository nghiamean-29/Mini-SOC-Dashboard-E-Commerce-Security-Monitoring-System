// =============================================================================
// Mini SOC - SQL validation harness
// -----------------------------------------------------------------------------
// WHAT IT DOES
//   Applies every Flyway migration (src/main/resources/db/migration/V*.sql) to a
//   REAL PostgreSQL engine and then tries to break it:
//     1. schema      - are all tables/indexes/constraints there?
//     2. rejections  - does the database refuse the bad data it is supposed to?
//     3. flow        - does an order -> event -> alert -> incident -> resolve
//                      cycle work end to end?
//     4. sequences   - do order_code_seq / incident_code_seq behave?
//     5. jsonb       - does the metadata column accept and answer @> queries?
//
// WHY IT EXISTS
//   `mvn test` runs the JPA slice tests, but the SQL of the migrations is only
//   exercised when Flyway executes it. This harness does exactly that - on the
//   same PostgreSQL dialect - so a broken migration is found in seconds instead
//   of at application start-up.
//
// HOW TO RUN
//   cd tools/sql-validation
//   npm install          # once: downloads PGlite (PostgreSQL compiled to WASM)
//   node validate.mjs
//
// Exit code 0 = every check passed. Any failure prints the failing check and
// exits with code 1 (so it can be used in CI later).
// =============================================================================

import { PGlite } from '@electric-sql/pglite';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const migrationDir = join(here, '..', '..', 'src', 'main', 'resources', 'db', 'migration');

let passed = 0;
let failed = 0;
const failures = [];

function ok(condition, label) {
  if (condition) {
    passed += 1;
  } else {
    failed += 1;
    failures.push(label);
    console.error(`  FAIL  ${label}`);
  }
}

function eq(actual, expected, label) {
  ok(actual === expected, `${label} (expected ${expected}, got ${actual})`);
}

/** Runs a statement that is EXPECTED to be rejected by the database. */
async function shouldFail(db, sql, label, params = []) {
  try {
    await db.query(sql, params);
    failed += 1;
    failures.push(label);
    console.error(`  FAIL  ${label} - the database accepted data it should have refused`);
  } catch (error) {
    passed += 1;
    const message = String(error.message).split('\n')[0];
    console.log(`  ok    ${label}  ->  ${message.slice(0, 90)}`);
  }
}

async function shouldSucceed(db, sql, label, params = []) {
  try {
    await db.query(sql, params);
    passed += 1;
    console.log(`  ok    ${label}`);
  } catch (error) {
    failed += 1;
    failures.push(label);
    console.error(`  FAIL  ${label}  ->  ${error.message}`);
  }
}

async function scalar(db, sql, params = []) {
  const result = await db.query(sql, params);
  const row = result.rows[0];
  return row ? Number(Object.values(row)[0]) : NaN;
}

async function main() {
  const db = new PGlite();
  const version = (await db.query('select version()')).rows[0].version;
  console.log(`\n=== Mini SOC - SQL validation on ${version} ===\n`);

  // ---------------------------------------------------------------- migrations
  console.log('1) Applying Flyway migrations');
  const files = readdirSync(migrationDir).filter((f) => f.endsWith('.sql')).sort();
  for (const file of files) {
    const sql = readFileSync(join(migrationDir, file), 'utf8');
    try {
      await db.exec(sql);
      passed += 1;
      console.log(`  ok    ${file}`);
    } catch (error) {
      failed += 1;
      failures.push(`migration ${file}`);
      console.error(`  FAIL  ${file}  ->  ${error.message}`);
      // A migration that cannot run makes every later check meaningless.
      console.error('\nAborting: fix the migration first.\n');
      process.exit(1);
    }
  }
  eq(files.length, 9, 'number of migration files');

  // ------------------------------------------------------------------- schema
  console.log('\n2) Schema shape');
  const expectedTables = [
    'alert_events', 'audit_logs', 'cart_items', 'carts', 'categories', 'incident_alerts',
    'incident_notes', 'login_history', 'order_items', 'orders', 'products', 'roles',
    'security_alerts', 'security_events', 'security_incidents', 'user_roles', 'users',
  ];
  const tables = (await db.query(
    "select table_name from information_schema.tables where table_schema = 'public' and table_name <> 'flyway_schema_history' order by table_name"
  )).rows.map((r) => r.table_name);
  eq(tables.length, expectedTables.length, 'application table count');
  ok(expectedTables.every((t) => tables.includes(t)),
    `all expected tables exist (missing: ${expectedTables.filter((t) => !tables.includes(t)).join(', ') || 'none'})`);

  const indexCount = await scalar(db,
    "select count(*) from pg_indexes where schemaname = 'public'");
  ok(indexCount >= 40, `index count is reasonable (${indexCount})`);

  const socIndexes = (await db.query(
    "select indexname from pg_indexes where schemaname = 'public'")).rows.map((r) => r.indexname);
  const requiredSocIndexes = [
    'idx_security_events_metadata_gin',   // V8 - the JSONB reporting index
    'idx_security_events_login_failed',   // V8 - partial index for the forensics query
    'idx_security_alerts_open',           // V8 - partial index for the analyst queue
    'idx_security_incidents_open',
    'idx_products_name_lower',            // V2 - functional index for the search
  ];
  ok(requiredSocIndexes.every((i) => socIndexes.includes(i)),
    `the reporting indexes exist (missing: ${requiredSocIndexes.filter((i) => !socIndexes.includes(i)).join(', ') || 'none'})`);

  const constraintCount = await scalar(db, `
    select count(*) from information_schema.table_constraints
    where table_schema = 'public' and constraint_type in ('PRIMARY KEY','UNIQUE','CHECK','FOREIGN KEY')`);
  ok(constraintCount >= 150, `constraint count is reasonable (${constraintCount})`);

  // ------------------------------------------------------------------ sequences
  console.log('\n3) Human readable code sequences');
  const seqNames = (await db.query(
    "select sequence_name from information_schema.sequences order by sequence_name"
  )).rows.map((r) => r.sequence_name);
  ok(seqNames.includes('order_code_seq'), 'order_code_seq exists');
  ok(seqNames.includes('incident_code_seq'), 'incident_code_seq exists');
  const orderNumberA = await scalar(db, "select nextval('order_code_seq')");
  const orderNumberB = await scalar(db, "select nextval('order_code_seq')");
  ok(orderNumberB > orderNumberA, `order_code_seq always increases (${orderNumberA} -> ${orderNumberB})`);
  const incidentNumber = await scalar(db, "select nextval('incident_code_seq')");
  ok(incidentNumber >= 1, `incident_code_seq returns a positive number (${incidentNumber})`);

  // -------------------------------------------------------------- seed contents
  console.log('\n4) Seed data (V9)');
  eq(await scalar(db, 'select count(*) from roles'), 3, 'exactly three roles are seeded');
  const roleCodes = (await db.query('select code from roles order by code')).rows.map((r) => r.code);
  ok(['ADMIN', 'CUSTOMER', 'SOC_ANALYST'].every((c) => roleCodes.includes(c)),
    `roles are CUSTOMER/ADMIN/SOC_ANALYST (found ${roleCodes.join(', ')})`);
  eq(await scalar(db, 'select count(*) from users'), 0, 'no user is seeded (no default credentials)');

  // ------------------------------------------------------- insert good baseline
  console.log('\n5) The database refuses bad data');
  await db.query(`insert into roles (code, name) values ('TEST_ROLE', 'Test role')`);

  await shouldFail(db, `insert into users (username, email, password_hash, status)
                        values ('bad user', 'valid@example.com', 'x', 'ACTIVE')`,
    'ck_users_username_format rejects "bad user"');
  await shouldFail(db, `insert into users (username, email, password_hash, status)
                        values ('validuser', 'valid2@example.com', 'x', 'SLEEPING')`,
    'ck_users_status rejects an unknown status');
  await db.query(`insert into users (username, email, password_hash, full_name)
                  values ('alice', 'alice@example.com', '$2a$10$abcdefghijklmnopqrstuv', 'Alice')`);
  await shouldFail(db, `insert into users (username, email, password_hash)
                        values ('alice', 'other@example.com', '$2a$10$hash')`,
    'uk_users_username rejects a duplicate username');

  const aliceId = await scalar(db, `select id from users where username = 'alice'`);
  const customerRoleId = await scalar(db, `select id from roles where code = 'CUSTOMER'`);
  const analystRoleId = await scalar(db, `select id from roles where code = 'SOC_ANALYST'`);
  await shouldSucceed(db, 'insert into user_roles (user_id, role_id) values ($1, $2)',
    'a role can be granted', [aliceId, customerRoleId]);
  await shouldFail(db, 'insert into user_roles (user_id, role_id) values ($1, $2)',
    'the same role cannot be granted twice (composite PK)', [aliceId, customerRoleId]);

  await shouldFail(db, `insert into login_history (user_id, username, success, failure_reason)
                        values ($1, 'alice', true, 'BAD_CREDENTIALS')`,
    'ck_login_history_reason rejects "success with a failure reason"', [aliceId]);
  await shouldFail(db, `insert into login_history (user_id, username, success, source_ip)
                        values ($1, 'alice', false, '203.0.113.5')`,
    'ck_login_history_reason refuses a failed login without a reason', [aliceId]);
  eq(await scalar(db, 'select count(*) from login_history'), 0,
    'nothing was stored by the rejected insert');

  await shouldSucceed(db, `insert into login_history (user_id, username, success, failure_reason, source_ip)
                           values ($1, 'alice', false, 'BAD_CREDENTIALS', '203.0.113.5')`,
    'a failed login with a reason is accepted', [aliceId]);

  await db.query(`insert into categories (name, slug) values ('Keyboards', 'keyboards')`);
  const categoryId = await scalar(db, `select id from categories where slug = 'keyboards'`);
  await shouldFail(db, `insert into products (category_id, name, slug, price, stock)
                        values ($1, 'Bad slug', 'Bad Slug', 10, 1)`,
    'ck_products_slug_format rejects "Bad Slug"', [categoryId]);
  await db.query(`insert into products (category_id, name, slug, price, stock)
                  values ($1, 'Yak 68', 'yak-68', 99.99, 10)`, [categoryId]);
  const productId = await scalar(db, `select id from products where slug = 'yak-68'`);
  await shouldFail(db, `update products set stock = -1 where id = $1`,
    'ck_products_stock rejects negative stock', [productId]);

  const cartId = await scalar(db, `insert into carts (user_id) values ($1) returning id`, [aliceId]);
  ok(Number.isFinite(cartId), 'a cart is created');
  await shouldFail(db, `insert into carts (user_id) values ($1)`,
    'uk_carts_user rejects a second cart for the same user', [aliceId]);
  await db.query(`insert into cart_items (cart_id, product_id, quantity, unit_price)
                  values ($1, $2, 2, 99.99)`, [cartId, productId]);
  await shouldFail(db, `insert into cart_items (cart_id, product_id, quantity, unit_price)
                        values ($1, $2, 1, 99.99)`,
    'uk_cart_items_cart_product rejects a duplicate line', [cartId, productId]);
  await shouldFail(db, `update cart_items set quantity = 1000 where cart_id = $1`,
    'ck_cart_items_quantity rejects quantity > 999', [cartId]);

  // ------------------------------------------------------------------- orders
  const orderNumber = await scalar(db, "select nextval('order_code_seq')");
  const orderCode = `ORD-20261011-${String(orderNumber).padStart(6, '0')}`;
  const orderId = await scalar(db,
    `insert into orders (order_code, user_id, total_amount, shipping_name)
     values ($1, $2, 199.98, 'Alice') returning id`, [orderCode, aliceId]);
  ok(Number.isFinite(orderId), `order ${orderCode} is created`);
  await db.query(`insert into order_items (order_id, product_id, product_name, unit_price, quantity, line_total)
                  values ($1, $2, 'Yak 68', 99.99, 2, 199.98)`, [orderId, productId]);
  await shouldFail(db, `update orders set status = 'CANCELLED' where id = $1`,
    'ck_orders_cancelled refuses CANCELLED without cancelled_at', [orderId]);
  await shouldFail(db, `update orders set status = 'ARCHIVED' where id = $1`,
    'ck_orders_status refuses an unknown state', [orderId]);
  await shouldFail(db, `insert into order_items (order_id, product_id, product_name, unit_price, quantity, line_total)
                        values ($1, $2, 'Yak 68', 99.99, 0, 0)`,
    'ck_order_items_quantity refuses quantity 0', [orderId, productId]);
  await shouldFail(db, `delete from users where id = $1`,
    'the order owner cannot be deleted (FK RESTRICT on a financial record)', [aliceId]);

  // ------------------------------------------------------------ security events
  console.log('\n6) Security events (append-only, jsonb)');
  await shouldFail(db, `insert into security_events (event_type) values ('NOT_AN_EVENT')`,
    'ck_security_events_type refuses an unknown event type');
  const eventId = await scalar(db, `
    insert into security_events (event_type, severity_hint, source_ip, username, endpoint,
                                 http_method, status_code, description, metadata, request_id)
    values ('LOGIN_FAILED', 'MEDIUM', '203.0.113.5', 'alice', '/login', 'POST', 401,
            'Failed login for alice',
            '{"rule":"BRUTE_FORCE","attempts":7,"simulated":true}'::jsonb,
            '3f1c2b4a-0000-4000-8000-000000000001')
    returning id`);
  ok(Number.isFinite(eventId), 'an event with a JSONB metadata document is stored');
  const jsonbHits = await scalar(db,
    `select count(*) from security_events where metadata @> '{"simulated": true}'::jsonb`);
  eq(jsonbHits, 1, 'the JSONB containment operator answers the GIN-indexed question');
  const jsonbValue = (await db.query(
    `select metadata ->> 'rule' as rule from security_events where id = $1`, [eventId])).rows[0].rule;
  eq(jsonbValue, 'BRUTE_FORCE', 'JSONB values can be read back by key');
  await shouldFail(db, `insert into security_events (event_type, status_code)
                        values ('API_REQUEST', 999)`,
    'ck_security_events_status_code refuses status 999');
  await shouldFail(db, `insert into security_events (event_type, http_method)
                        values ('API_REQUEST', 'FETCH')`,
    'ck_security_events_http_method refuses an unknown method');

  // ------------------------------------------------------------------- alerts
  console.log('\n7) Alerts and incidents');
  const dedupKey = 'BRUTE_FORCE|203.0.113.5|20261011T00:00';
  const alertId = await scalar(db, `
    insert into security_alerts (alert_type, severity, source_ip, username, description,
                                 status, related_event_count, dedup_key, rule_name)
    values ('BRUTE_FORCE', 'HIGH', '203.0.113.5', 'alice', '7 failed logins from one IP',
            'OPEN', 7, $1, 'BruteForceRule') returning id`, [dedupKey]);
  ok(Number.isFinite(alertId), 'a brute force alert is raised');
  await shouldFail(db, `
    insert into security_alerts (alert_type, severity, source_ip, dedup_key)
    values ('BRUTE_FORCE', 'HIGH', '203.0.113.5', $1)`,
    'uk_security_alerts_dedup refuses a second alert in the same cooldown bucket', [dedupKey]);
  await shouldFail(db, `update security_alerts set related_event_count = 0 where id = $1`,
    'ck_security_alerts_related_events refuses a zero counter', [alertId]);
  await shouldSucceed(db, `insert into alert_events (alert_id, event_id) values ($1, $2)`,
    'the alert is linked to its evidence', [alertId, eventId]);
  await shouldFail(db, `insert into alert_events (alert_id, event_id) values ($1, $2)`,
    'the same evidence cannot be linked twice (composite PK)', [alertId, eventId]);

  const incidentCodeNumber = await scalar(db, "select nextval('incident_code_seq')");
  const incidentCode = `INC-${String(incidentCodeNumber).padStart(4, '0')}`;
  const incidentId = await scalar(db, `
    insert into security_incidents (incident_code, title, incident_type, severity, source_ip,
                                    status, alert_count, description)
    values ($1, 'Possible brute force attack from 203.0.113.5',
            'POSSIBLE_BRUTE_FORCE_ATTACK', 'HIGH', '203.0.113.5', 'OPEN', 1, 'Correlated alert')
    returning id`, [incidentCode]);
  ok(Number.isFinite(incidentId), `incident ${incidentCode} is opened`);
  await shouldFail(db, `insert into security_incidents (incident_code, title, incident_type, severity)
                        values ($1, 'Duplicate code', 'POSSIBLE_REQUEST_FLOOD', 'LOW')`,
    'uk_security_incidents_code refuses a duplicate incident code', [incidentCode]);
  await shouldFail(db, `update security_incidents set status = 'RESOLVED' where id = $1`,
    'ck_security_incidents_resolved refuses RESOLVED without resolved_at', [incidentId]);
  await shouldSucceed(db, `update security_incidents set status = 'RESOLVED', resolved_at = now(),
                                  resolution = 'Blocked the IP at the reverse proxy'
                           where id = $1`,
    'a resolved incident with a timestamp and a resolution is accepted', [incidentId]);
  await shouldSucceed(db, `insert into incident_alerts (incident_id, alert_id) values ($1, $2)`,
    'the alert is attached to the incident', [incidentId, alertId]);
  await shouldFail(db, `insert into incident_alerts (incident_id, alert_id) values ($1, $2)`,
    'the same alert cannot be attached twice (idempotency, DB-8)', [incidentId, alertId]);
  await shouldFail(db, `insert into incident_notes (incident_id, content) values ($1, '   ')`,
    'ck_incident_notes_content refuses a blank note', [incidentId]);
  await shouldSucceed(db, `insert into incident_notes (incident_id, author_id, author_username, content)
                           values ($1, $2, 'alice', 'Checked the auth logs: 7 failures from one IP.')`,
    'an investigation note is stored', [incidentId, aliceId]);

  // --------------------------------------------------------- end-to-end report
  console.log('\n8) The whole story, read back from the database');
  const story = (await db.query(`
    select i.incident_code, i.status, i.severity, a.alert_type, a.related_event_count,
           (select count(*) from incident_alerts ia where ia.incident_id = i.id) as linked_alerts,
           (select count(*) from alert_events ae where ae.alert_id = a.id) as linked_events,
           (select count(*) from incident_notes n where n.incident_id = i.id) as note_count
    from security_incidents i
    join incident_alerts ia on ia.incident_id = i.id
    join security_alerts a on a.id = ia.alert_id
    where i.id = $1`, [incidentId])).rows[0];
  console.log(`  incident ${story.incident_code} (${story.status}) <- alert ${story.alert_type} ${story.severity}`
    + ` <- ${story.linked_events} linked event(s), ${story.note_count} note(s), ${story.linked_alerts} linked alert(s)`);
  ok(story.incident_code === incidentCode && story.linked_events === 1 && story.note_count === 1,
    'the incident -> alert -> event -> note chain is complete');

  const alertCounters = (await db.query(`
    select a.alert_type, count(*) as total,
           sum(case when a.status = 'FALSE_POSITIVE' then 1 else 0 end) as false_positives
    from security_alerts a group by a.alert_type`)).rows[0];
  eq(Number(alertCounters.total), 1, 'the rule quality report sees exactly one alert');

  // ------------------------------------------------------------ retention path
  console.log('\n9) Retention (bulk delete)');
  const deletionCount = (await db.query(
    `delete from security_events where occurred_at < now() - interval '30 days'`)).affectedRows;
  eq(Number(deletionCount), 0, 'the retention delete runs (nothing is old enough yet)');

  // -------------------------------------------------------------------- report
  console.log(`\n=== ${passed} passed, ${failed} failed ===`);
  if (failed > 0) {
    console.log('Failed checks:');
    failures.forEach((f) => console.log(`  - ${f}`));
  }
  await db.close();
  process.exit(failed === 0 ? 0 : 1);
}

main().catch((error) => {
  console.error('\nHarness crashed:', error);
  process.exit(1);
});
