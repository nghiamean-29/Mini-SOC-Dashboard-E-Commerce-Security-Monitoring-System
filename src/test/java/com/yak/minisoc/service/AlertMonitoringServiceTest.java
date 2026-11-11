package com.yak.minisoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yak.minisoc.entity.security.AlertStatus;
import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.view.AlertMonitoringView;
import jakarta.annotation.security.RolesAllowed;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class AlertMonitoringServiceTest {

    private final SecurityAlertRepository alerts = mock(SecurityAlertRepository.class);
    private final SecurityIncidentRepository incidents = mock(SecurityIncidentRepository.class);
    private final AlertMonitoringService service = new AlertMonitoringService(alerts, incidents);

    @Test
    void passesAllFiltersAndRequestedPageToRepository() {
        var request = PageRequest.of(2, AlertMonitoringService.PAGE_SIZE);
        when(alerts.findForMonitoring(Severity.HIGH, AlertStatus.OPEN, AlertType.BRUTE_FORCE,
            "2001:db8::1", request)).thenReturn(Page.empty(request));

        var page = service.list(Severity.HIGH, AlertStatus.OPEN, AlertType.BRUTE_FORCE, " 2001:db8::1 ", 2);

        assertThat(page.getNumber()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(25);
        verify(alerts).findForMonitoring(Severity.HIGH, AlertStatus.OPEN, AlertType.BRUTE_FORCE,
            "2001:db8::1", request);
        verifyNoInteractions(incidents);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t "})
    void treatsBlankSourceIpAsUnfiltered(String input) {
        var request = PageRequest.of(0, AlertMonitoringService.PAGE_SIZE);
        when(alerts.findForMonitoring(null, null, null, null, request)).thenReturn(Page.empty(request));

        assertThat(service.list(null, null, null, input, 0)).isEmpty();
        verify(alerts).findForMonitoring(null, null, null, null, request);
    }

    @Test
    void rejectsInvalidPageBeforeQuerying() {
        assertThatThrownBy(() -> service.list(null, null, null, null, -1))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(alerts, incidents);
    }

    @Test
    void skipsIncidentLookupForMissingAlert() {
        when(alerts.findWithEventsById(99L)).thenReturn(Optional.empty());

        assertThat(service.details(99L)).isEmpty();
        verifyNoInteractions(incidents);
    }

    @Test
    void vaadinViewDeclaresBothSocRoles() {
        assertThat(AlertMonitoringView.class.getAnnotation(RolesAllowed.class).value())
            .containsExactlyInAnyOrder("ADMIN", "SOC_ANALYST");
    }
}
