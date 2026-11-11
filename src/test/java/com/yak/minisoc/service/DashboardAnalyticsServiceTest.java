package com.yak.minisoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityEventRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.service.DashboardAnalyticsService.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class DashboardAnalyticsServiceTest {

    private final SecurityEventRepository events = mock(SecurityEventRepository.class);
    private final SecurityAlertRepository alerts = mock(SecurityAlertRepository.class);
    private final SecurityIncidentRepository incidents = mock(SecurityIncidentRepository.class);
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    private DashboardAnalyticsService service;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    @BeforeEach
    void setUp() {
        context.register(MethodSecurity.class);
        context.registerBean(DashboardAnalyticsService.class, () -> new DashboardAnalyticsService(events, alerts, incidents));
        context.refresh();
        service = context.getBean(DashboardAnalyticsService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void rejectsUnauthenticatedUsersAndCustomersBeforeQuerying() {
        assertThatThrownBy(() -> service.getAnalytics(Window.LAST_24_HOURS))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        authenticate("CUSTOMER");
        assertThatThrownBy(() -> service.getAnalytics(Window.LAST_24_HOURS))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(events, alerts, incidents);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SOC_ANALYST"})
    void privilegedRolesGetZeroFilledAnalyticsWhenNoRowsExist(String role) {
        authenticate(role);
        var analytics = service.getAnalytics(Window.LAST_7_DAYS);

        assertThat(analytics.timeline()).hasSizeBetween(7, 8).allMatch(bucket -> bucket.total() == 0);
        assertThat(analytics.severity()).allMatch(count -> count.total() == 0);
        assertThat(analytics.incidentStatuses()).allMatch(count -> count.total() == 0);
        assertThat(analytics.attackTypes()).isEmpty();
        assertThat(analytics.sourceIps()).isEmpty();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            "analyst", "unused", AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }
}
