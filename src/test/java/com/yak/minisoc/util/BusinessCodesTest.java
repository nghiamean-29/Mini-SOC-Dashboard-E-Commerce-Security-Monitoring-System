package com.yak.minisoc.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Plain unit tests for {@link BusinessCodes} - no Spring, no database, milliseconds to run.
 *
 * <p>They exist because the codes are part of the database contract (column widths and
 * UNIQUE constraints). A format change that silently produces a 31-character string would
 * otherwise only be discovered when a real customer checks out.</p>
 */
class BusinessCodesTest {

    @Test
    @DisplayName("order code: ORD-<yyyyMMdd>-<6 digits>")
    void orderCodeFormat() {
        String code = BusinessCodes.orderCode(Instant.parse("2026-10-11T09:30:00Z"), 42);

        assertThat(code).isEqualTo("ORD-20261011-000042");
        assertThat(code).matches("ORD-\\d{8}-\\d{6}");
        assertThat(code.length()).isLessThanOrEqualTo(BusinessCodes.ORDER_CODE_MAX_LENGTH);
    }

    @Test
    @DisplayName("the date part is UTC, so the same code means the same day everywhere")
    void orderCodeUsesUtc() {
        // 2026-10-10T18:00:00Z is already 2026-10-11 in Ho Chi Minh City (UTC+7), but the
        // database, the logs and the receipt must agree on one definition: UTC.
        String code = BusinessCodes.orderCode(Instant.parse("2026-10-10T18:00:00Z"), 7);

        assertThat(code).startsWith("ORD-20261010-");
    }

    @Test
    @DisplayName("the number is zero padded but never truncated")
    void orderCodePadsOnly() {
        assertThat(BusinessCodes.orderCode(Instant.parse("2026-01-02T00:00:00Z"), 1))
                .isEqualTo("ORD-20260102-000001");
        assertThat(BusinessCodes.orderCode(Instant.parse("2026-01-02T00:00:00Z"), 1234567))
                .isEqualTo("ORD-20260102-1234567");
        assertThat(BusinessCodes.orderCode(Instant.parse("2026-01-02T00:00:00Z"), 1234567).length())
                .isLessThanOrEqualTo(BusinessCodes.ORDER_CODE_MAX_LENGTH);
    }

    @Test
    @DisplayName("incident code: INC-0001 style, readable in a report")
    void incidentCodeFormat() {
        assertThat(BusinessCodes.incidentCode(1)).isEqualTo("INC-0001");
        assertThat(BusinessCodes.incidentCode(7)).isEqualTo("INC-0007");
        assertThat(BusinessCodes.incidentCode(12345)).isEqualTo("INC-12345");
        assertThat(BusinessCodes.incidentCode(12345).length())
                .isLessThanOrEqualTo(BusinessCodes.INCIDENT_CODE_MAX_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -999})
    @DisplayName("a sequence number must be positive - nextval never returns 0")
    void rejectsNonPositiveNumbers(long invalid) {
        assertThatThrownBy(() -> BusinessCodes.orderCode(Instant.now(), invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order");
        assertThatThrownBy(() -> BusinessCodes.incidentCode(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incident");
    }

    @Test
    @DisplayName("a null timestamp is refused (never invent the date of a code)")
    void rejectsNullTimestamp() {
        assertThatThrownBy(() -> BusinessCodes.orderCode(null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
