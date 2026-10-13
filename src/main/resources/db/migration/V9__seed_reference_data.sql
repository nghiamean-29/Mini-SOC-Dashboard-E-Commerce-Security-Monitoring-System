-- =============================================================================
-- V9 : REFERENCE DATA (role rows)
-- -----------------------------------------------------------------------------
-- Why seed in a migration and not in Java code (data.sql / CommandLineRunner)?
--   The three roles are part of the application's contract: the security config,
--   the @PreAuthorize expressions and the detection rules all refer to these
--   codes. Every environment (dev, test, prod) needs exactly the same rows, and
--   it must happen exactly once - which is the definition of a reference-data
--   migration.
--
-- What is deliberately NOT seeded: users.
--   A seeded account means a documented default credential, which is one of the
--   most common real-world vulnerabilities ("admin/admin" left in production).
--   Phase 3 creates the first administrator explicitly, from environment
--   variables, at first startup - see docs/PHASE-03-*.md.
-- =============================================================================

INSERT INTO roles (code, name, description)
VALUES ('CUSTOMER', 'Customer',
        'Shopper: browses products, manages a cart, places and cancels their own orders'),
       ('ADMIN', 'Administrator',
        'Manages categories, products and orders; may also view the SOC dashboard'),
       ('SOC_ANALYST', 'SOC Analyst',
        'Monitors security events, investigates alerts and handles incidents');

-- Sanity check inside the migration itself: if the insert somehow did not
-- produce exactly three rows, the migration fails and the schema is rolled back.
-- Flyway runs each migration inside a transaction on PostgreSQL, so this
-- guards the next migrations against an inconsistent state.
DO $$
DECLARE
    role_count INTEGER;
BEGIN
    SELECT count(*) INTO role_count FROM roles;
    IF role_count <> 3 THEN
        RAISE EXCEPTION 'Expected exactly 3 roles after seeding, found %', role_count;
    END IF;
END
$$;
