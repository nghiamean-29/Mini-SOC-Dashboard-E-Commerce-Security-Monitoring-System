package com.yak.minisoc.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class IncidentMonitoringServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final IncidentManagementService management = mock(IncidentManagementService.class);
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    private IncidentMonitoringService service;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    @BeforeEach
    void setUp() {
        context.register(MethodSecurity.class);
        context.registerBean(IncidentMonitoringService.class, () -> new IncidentMonitoringService(
            mock(SecurityIncidentRepository.class), users, management));
        context.refresh();
        service = context.getBean(IncidentMonitoringService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void rejectsCustomerBeforeCallingMutationDependencies() {
        authenticate("CUSTOMER");

        assertThatThrownBy(() -> service.assign(10L, 99L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.start(10L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.addNote(10L, "Evidence")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.resolve(10L, "Contained")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.markFalsePositive(10L, "Expected traffic"))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(users, management);
    }

    @Test
    void usesAuthenticatedAnalystIdAndNormalizesNote() {
        authenticate("SOC_ANALYST");
        account(UserStatus.ACTIVE, "SOC_ANALYST");

        service.addNote(10L, "  Evidence  ");

        verify(management).addInvestigationNote(10L, 42L, "Evidence");
    }

    @Test
    void deniesDisabledAccountWithExistingPrivilegedSession() {
        authenticate("ADMIN");
        account(UserStatus.DISABLED, "ADMIN");

        assertThatThrownBy(() -> service.start(10L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(management);
    }

    @Test
    void rejectsBlankAndOversizedClosureText() {
        authenticate("ADMIN");
        account(UserStatus.ACTIVE, "ADMIN");

        assertThatThrownBy(() -> service.resolve(10L, "  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.markFalsePositive(10L,
            "x".repeat(IncidentMonitoringService.MAX_TEXT_LENGTH + 1)))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(management);
    }

    private void account(UserStatus status, String role) {
        User actor = mock(User.class);
        when(actor.getId()).thenReturn(42L);
        when(actor.getStatus()).thenReturn(status);
        when(actor.hasRole(role)).thenReturn(true);
        when(users.findWithRolesByUsername("operator")).thenReturn(Optional.of(actor));
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("operator", null,
                AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }
}
