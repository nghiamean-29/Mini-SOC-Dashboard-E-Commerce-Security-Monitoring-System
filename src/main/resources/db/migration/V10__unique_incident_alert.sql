
-- =====================================================
-- V10 - INCIDENT DEDUPLICATION
-- =====================================================

-- Each security alert belongs to at most one incident.
-- One incident can contain multiple alerts.

ALTER TABLE incident_alerts
    ADD CONSTRAINT uq_incident_alerts_alert_id
        UNIQUE (alert_id);
