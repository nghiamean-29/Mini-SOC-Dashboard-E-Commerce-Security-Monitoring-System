package com.yak.minisoc.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Builds the two human readable business codes of the project:
 * {@code ORD-20261011-000042} and {@code INC-0007}.
 *
 * <h3>Why the format lives in one class instead of inside two services</h3>
 * <ul>
 *   <li><strong>It is part of the database contract.</strong> {@code orders.order_code}
 *       is {@code VARCHAR(30) UNIQUE}; {@code security_incidents.incident_code} is
 *       {@code VARCHAR(20) UNIQUE}. If two services built the same code differently,
 *       uniqueness would still hold but the shop would show two formats to the customer.
 *       One class, one format, one test.</li>
 *   <li><strong>Testability without a database.</strong> The methods take the timestamp as
 *       a parameter instead of calling {@code Instant.now()} internally, so a test can pin
 *       the date ("codes of the same day share the date part") instead of depending on the
 *       day the test happens to run.</li>
 *   <li><strong>Length safety.</strong> Both formats are checked against the column widths
 *       by a unit test, so the "value too long for type character varying" error can never
 *       appear in production.</li>
 * </ul>
 *
 * <h3>The numbers come from PostgreSQL sequences</h3>
 * <p>{@code ORD-...} numbering uses {@code order_code_seq} and {@code INC-...} uses
 * {@code incident_code_seq}; both numbers are obtained with a native
 * {@code nextval()} query in the corresponding repository. A sequence cannot return the
 * same value twice, so two simultaneous requests cannot generate the same code - which is
 * what {@code COUNT(*) + 1} would do (see the comment in migration V4). The price is a gap
 * when a transaction rolls back, which is harmless for a shop identifier and documented
 * rather than hidden.</p>
 *
 * <p><strong>Time zone:</strong> the date part uses UTC ({@link ZoneOffset#UTC}), not the
 * server default. An order placed at 07:00 in Ho Chi Minh City (00:00 UTC) must carry the
 * same date on the receipt, in the database and in the log file, no matter which machine
 * the application runs on - that only works if "the date of a code" is defined once.</p>
 */
public final class BusinessCodes {

    /** Column width of {@code orders.order_code}; kept next to the format for the test. */
    public static final int ORDER_CODE_MAX_LENGTH = 30;

    /** Column width of {@code security_incidents.incident_code}. */
    public static final int INCIDENT_CODE_MAX_LENGTH = 20;

    private static final String ORDER_PREFIX = "ORD";

    private static final String INCIDENT_PREFIX = "INC";

    private static final DateTimeFormatter DATE_PART =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private BusinessCodes() {
        // Utility class: never instantiated.
    }

    /**
     * {@code ORD-<yyyyMMdd>-<6 digits>}, e.g. {@code ORD-20261011-000042}.
     *
     * @param createdAt the moment the order was created (the caller's clock)
     * @param sequenceNumber the value returned by {@code nextval('order_code_seq')};
     *        must be positive
     */
    public static String orderCode(Instant createdAt, long sequenceNumber) {
        Objects.requireNonNull(createdAt, "createdAt");
        requirePositive(sequenceNumber, "order");
        return ORDER_PREFIX + "-" + DATE_PART.format(createdAt) + "-"
                + String.format("%06d", sequenceNumber);
    }

    /**
     * {@code INC-<4 digits>}, e.g. {@code INC-0007}.
     *
     * <p>No date part: unlike an order, an incident is identified by its position in the
     * SOC's own log ("the seventh incident"), and the date is already a column of its own
     * ({@code detected_at}).</p>
     *
     * @param sequenceNumber the value returned by {@code nextval('incident_code_seq')}
     */
    public static String incidentCode(long sequenceNumber) {
        requirePositive(sequenceNumber, "incident");
        return INCIDENT_PREFIX + "-" + String.format("%04d", sequenceNumber);
    }

    private static void requirePositive(long value, String what) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    what + " sequence number must be positive, was " + value);
        }
    }
}
