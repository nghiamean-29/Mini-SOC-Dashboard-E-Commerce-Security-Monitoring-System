-- =============================================================================
-- V8 : SOC REPORTING INDEXES
-- -----------------------------------------------------------------------------
-- V1..V7 created the tables and their basic indexes. This migration adds the
-- indexes that exist purely because of HOW THE DASHBOARD QUERIES THE DATA
-- (Phase 9) - they are a performance decision, not part of the data model.
--
-- Why a separate migration instead of editing V5/V6?
--   Flyway treats an already applied file as immutable (it stores a checksum).
--   Editing V5 after somebody has run it would break their database. New index
--   = new file. That rule is the backbone of safe schema evolution.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Partial indexes: an analyst only works on OPEN/INVESTIGATING items, which is a
-- few percent of all rows. A partial index indexes only that subset, so it is
-- much smaller than a full index and stays fast as history grows.
-- -----------------------------------------------------------------------------
CREATE INDEX idx_security_alerts_open
    ON security_alerts (detected_at DESC)
    WHERE status IN ('OPEN', 'INVESTIGATING');

CREATE INDEX idx_security_incidents_open
    ON security_incidents (severity DESC, detected_at DESC)
    WHERE status IN ('OPEN', 'INVESTIGATING');

-- -----------------------------------------------------------------------------
-- Brute force forensics: "show me every failed login from this IP".
-- The rule engine keeps its counters in memory, but the analyst still has to be
-- able to justify the alert by reading the raw events.
-- -----------------------------------------------------------------------------
CREATE INDEX idx_security_events_login_failed
    ON security_events (source_ip, occurred_at DESC)
    WHERE event_type = 'LOGIN_FAILED';

-- -----------------------------------------------------------------------------
-- "Top attacking IP" report (Phase 9): count alerts grouped by source_ip.
-- -----------------------------------------------------------------------------
CREATE INDEX idx_security_alerts_top_ips
    ON security_alerts (source_ip, severity);

-- -----------------------------------------------------------------------------
-- Dashboard time series: events per minute/hour, optionally split by type.
-- occurred_at is already indexed alone (V5); adding event_type first lets the
-- same index serve "events of type X over time" without a sort.
-- -----------------------------------------------------------------------------
CREATE INDEX idx_security_events_type_occurred
    ON security_events (event_type, occurred_at DESC);

-- -----------------------------------------------------------------------------
-- JSONB containment queries (metadata @> '{"order_code":"ORD-..."}').
-- jsonb_path_ops is smaller and faster than the default jsonb_ops for the
-- containment operator, which is the only JSON query shape this project uses.
-- -----------------------------------------------------------------------------
CREATE INDEX idx_security_events_metadata_gin
    ON security_events USING gin (metadata jsonb_path_ops);
