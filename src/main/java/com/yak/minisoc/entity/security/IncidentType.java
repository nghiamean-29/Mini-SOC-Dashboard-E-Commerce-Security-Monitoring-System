package com.yak.minisoc.entity.security;

/**
 * The nature of an incident ({@code security_incidents.incident_type}).
 *
 * <p>An incident is built by correlating alerts, so its type is derived from the alerts it
 * contains - not invented by the analyst. Examples of the mapping applied in Phase 8:</p>
 * <ul>
 *   <li>several {@link AlertType#BRUTE_FORCE} alerts &rarr;
 *       {@link #POSSIBLE_BRUTE_FORCE_ATTACK}</li>
 *   <li>a brute-force alert followed by a successful login and changes &rarr;
 *       {@link #SUSPECTED_ACCOUNT_TAKEOVER}</li>
 *   <li>alerts of different kinds from the same source &rarr;
 *       {@link #MIXED_SUSPICIOUS_ACTIVITY}</li>
 * </ul>
 *
 * <p>The word "possible"/"suspected" in every label is deliberate wording: at this stage
 * the SOC has evidence, not proof, and an incident title that claims more than the data
 * supports misleads the analyst who reads it three weeks later.</p>
 *
 * <p>Each constant carries a human readable label used to build the incident title
 * (for example "Possible brute force attack from 203.0.113.45"). Keeping the wording in
 * the enum means the dashboard, the reports and the database all use the same phrase.</p>
 */
public enum IncidentType {

    POSSIBLE_BRUTE_FORCE_ATTACK("Possible brute force attack"),
    POSSIBLE_REQUEST_FLOOD("Possible request flood"),
    POSSIBLE_SQL_INJECTION("Possible SQL injection attempt"),
    POSSIBLE_UNAUTHORIZED_ACCESS("Possible unauthorized access attempt"),
    SUSPECTED_ACCOUNT_TAKEOVER("Suspected account takeover"),
    MIXED_SUSPICIOUS_ACTIVITY("Mixed suspicious activity");

    private final String label;

    IncidentType(String label) {
        this.label = label;
    }

    /** Human readable phrase, e.g. "Possible brute force attack". */
    public String label() {
        return label;
    }
}
