package com.yak.minisoc.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.yak.minisoc.config.AdminBootstrap;
import com.yak.minisoc.entity.Order;
import com.yak.minisoc.entity.OrderStatus;
import com.yak.minisoc.entity.Product;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.repository.OrderRepository;
import com.yak.minisoc.repository.ProductRepository;
import com.yak.minisoc.repository.RoleRepository;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.UserRepository;
import com.yak.minisoc.service.AlertMonitoringService;
import com.yak.minisoc.service.AuthenticationService;
import com.yak.minisoc.service.CheckoutService;
import com.yak.minisoc.service.RegistrationService;
import com.yak.minisoc.service.IncidentManagementService;
import com.yak.minisoc.service.IncidentMonitoringService;
import com.yak.minisoc.service.OrderService;
import com.yak.minisoc.util.BusinessCodes;
import com.yak.minisoc.view.AlertMonitoringView;
import com.yak.minisoc.view.CartView;
import com.yak.minisoc.view.CheckoutView;
import com.yak.minisoc.view.DashboardView;
import com.yak.minisoc.view.IncidentMonitoringView;
import com.yak.minisoc.view.LoginView;
import com.yak.minisoc.view.OrderHistoryView;
import com.yak.minisoc.view.RegisterView;
import com.yak.minisoc.view.SecurityEventsView;
import com.yak.minisoc.view.ShopView;
import jakarta.servlet.RequestDispatcher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockRequestDispatcher;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.WebAttributes;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "vaadin.productionMode=true")
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseGuard {
        @Bean
        FlywayMigrationStrategy testDatabaseMigration() {
            return flyway -> {
                String database = new JdbcTemplate(flyway.getConfiguration().getDataSource())
                    .queryForObject("SELECT current_database()", String.class);
                if (!"minisoc_test".equals(database)) {
                    throw new IllegalStateException("Expected PostgreSQL database minisoc_test, found " + database);
                }
                flyway.migrate();
            };
        }
    }

    @MockitoBean
    private AdminBootstrap adminBootstrap;

    @Autowired
    private WebApplicationContext application;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private SecurityAlertRepository alerts;
    @Autowired
    private AlertMonitoringService alertMonitoring;
    @Autowired
    private IncidentMonitoringService incidentMonitoring;
    @Autowired
    private IncidentManagementService incidentManagement;
    @Autowired
    private AuthenticationService desktopAuthentication;

    @Autowired
    private OrderRepository orders;
    @Autowired
    private ProductRepository products;
    @Autowired
    private OrderService orderService;

    @Autowired
    private CheckoutService checkout;
    @Autowired
    private RegistrationService registration;

    private MockMvc mvc;

    @BeforeEach
    void buildMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(application).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/admin/settings", "/actuator/info", "/soc/alerts", "/soc/incidents",
        "/soc/events", "/cart", "/cart/items", "/checkout", "/orders", "/orders/detail", "/shop"})
    void redirectsAnonymousDirectRequestsToLogin(String path) throws Exception {
        var response = mvc.perform(get(path).accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection()).andExpect(unauthenticated())
            .andReturn().getResponse();
        assertThat(response.getRedirectedUrl()).endsWith("/login");
    }

    @ParameterizedTest
    @CsvSource({
        "CUSTOMER, /admin/settings", "SOC_ANALYST, /admin/settings",
        "CUSTOMER, /actuator/info", "SOC_ANALYST, /actuator/info",
        "CUSTOMER, /soc/alerts", "CUSTOMER, /soc/incidents", "CUSTOMER, /soc/events",
        "ADMIN, /cart", "SOC_ANALYST, /cart/items", "SOC_ANALYST, /checkout", "ADMIN, /orders/detail"
    })
    void rejectsWrongRoleBeforeDirectRequestReachesVaadin(String role, String path) throws Exception {
        mvc.perform(get(path).with(user("fixture").roles(role))).andExpect(status().isForbidden());
    }

    @Test
    void administratorCanReadActuatorInfo() throws Exception {
        mvc.perform(get("/actuator/info").with(user("fixture").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    static Stream<Arguments> viewRoles() {
        Set<String> all = Set.of("ADMIN", "SOC_ANALYST", "CUSTOMER");
        Set<String> soc = Set.of("ADMIN", "SOC_ANALYST");
        Set<String> customer = Set.of("CUSTOMER");
        return Stream.of(
            Arguments.of(LoginView.class, true, all),
            Arguments.of(RegisterView.class, true, all),
            Arguments.of(ShopView.class, false, all),
            Arguments.of(DashboardView.class, false, soc),
            Arguments.of(AlertMonitoringView.class, false, soc),
            Arguments.of(IncidentMonitoringView.class, false, soc),
            Arguments.of(SecurityEventsView.class, false, soc),
            Arguments.of(CartView.class, false, customer),
            Arguments.of(CheckoutView.class, false, customer),
            Arguments.of(OrderHistoryView.class, false, customer));
    }

    @ParameterizedTest
    @MethodSource("viewRoles")
    void vaadinViewAccessMatchesRoleMatrix(Class<?> view, boolean anonymous, Set<String> allowedRoles) {
        var checker = new AccessAnnotationChecker();
        assertThat(checker.hasAccess(view, null, role -> false)).isEqualTo(anonymous);
        for (String role : Set.of("ADMIN", "SOC_ANALYST", "CUSTOMER")) {
            assertThat(checker.hasAccess(view, () -> "fixture", role::equals))
                .as("%s with %s", view.getSimpleName(), role).isEqualTo(allowedRoles.contains(role));
        }
    }

    @Test
    void loginRotatesSessionAndLogoutRequiresCsrf() throws Exception {
        String password = UUID.randomUUID().toString();
        User customer = account("CUSTOMER", UserStatus.ACTIVE, password);
        var session = new MockHttpSession(application.getServletContext());
        String previousId = session.getId();

        mvc.perform(post("/login").session(session).param("v-r", "uidl")
                .param("username", customer.getUsername()).param("password", password))
            .andExpect(status().isForbidden()).andExpect(unauthenticated());
        mvc.perform(post("/login").session(session).with(csrf()).param("username", customer.getUsername())
                .param("password", UUID.randomUUID().toString()))
            .andExpect(status().is3xxRedirection()).andExpect(unauthenticated());
        mvc.perform(post("/login").session(session).with(csrf()).param("username", customer.getUsername())
                .param("password", password))
            .andExpect(status().is3xxRedirection())
            .andExpect(authenticated().withUsername(customer.getUsername()).withRoles("CUSTOMER"));

        assertThat(session.getId()).isNotEqualTo(previousId);
        mvc.perform(post("/logout").session(session))
            .andExpect(status().isForbidden()).andExpect(authenticated());
        assertThat(session.isInvalid()).isFalse();
        mvc.perform(post("/logout").session(session).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/logout"})
    void loginAndLogoutRejectInvalidCsrfEvenWithVaadinRequestMarker(String path) throws Exception {
        mvc.perform(post(path).with(csrf().useInvalidToken()).param("v-r", "uidl"))
            .andExpect(status().isForbidden());
    }

    @Test
    void onlyTheVaadinServletRootAcceptsTheUidlMarkerWithoutSpringCsrf() throws Exception {
        // MockMvc can verify the forward, while the real servlet is responsible for UIDL token validation.
        var servletContext = (MockServletContext) application.getServletContext();
        servletContext.registerNamedDispatcher("springServlet", new MockRequestDispatcher("/vaadin-servlet"));
        try {
            mvc.perform(post("/").param("v-r", "uidl").contentType(MediaType.APPLICATION_JSON))
                .andExpect(forwardedUrl("/vaadin-servlet"));
            mvc.perform(post("/").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
            mvc.perform(post("/register").param("v-r", "uidl").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        } finally {
            servletContext.unregisterNamedDispatcher("springServlet");
        }
    }

    @Test
    void sqlInjectionUsernameCannotAuthenticateWithAnExistingPassword() throws Exception {
        String password = UUID.randomUUID().toString();
        User customer = account("CUSTOMER", UserStatus.ACTIVE, password);

        mvc.perform(post("/login").with(csrf())
                .param("username", customer.getUsername() + "' OR '1'='1' --")
                .param("password", password))
            .andExpect(status().is3xxRedirection()).andExpect(unauthenticated());

        mvc.perform(post("/login").with(csrf())
                .param("username", customer.getUsername()).param("password", password))
            .andExpect(authenticated().withUsername(customer.getUsername()));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500})
    void errorResponseHidesExceptionDetailsEvenWhenRequested(int code) throws Exception {
        String internalDetail = "internal-sql-detail-" + UUID.randomUUID();

        mvc.perform(get("/error").accept(MediaType.APPLICATION_JSON)
                .param("trace", "true").param("message", "true").param("errors", "true")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, code)
                .requestAttr(RequestDispatcher.ERROR_MESSAGE, internalDetail)
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException(internalDetail))
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/test-failure"))
            .andExpect(status().is(code))
            .andExpect(jsonPath("$.status").value(code))
            .andExpect(jsonPath("$.path").value("/test-failure"))
            .andExpect(jsonPath("$.message").doesNotExist())
            .andExpect(jsonPath("$.exception").doesNotExist())
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                .doesNotContain(internalDetail, "IllegalStateException"));
    }

    @Test
    void registrationRejectsNulInEmailBeforePersistence() {
        String username = "probe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = UUID.randomUUID().toString();

        assertThatThrownBy(() -> registration.register(username, "probe\0x@example.com", password))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Email không hợp lệ.");
        assertThat(users.findByUsername(username)).isEmpty();
    }

    @Test
    void registrationPersistsNormalizedValidEmail() {
        String username = "probe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        registration.register(username, "  " + username + "@EXAMPLE.COM  ", UUID.randomUUID().toString());
        assertThat(users.findByUsername(username)).hasValueSatisfying(user ->
            assertThat(user.getEmail()).isEqualTo(username + "@example.com"));
    }

    @ParameterizedTest
    @CsvSource({"Nguyễn Văn An, bad\0note", "bad\0name, Giao giờ hành chính"})
    @WithMockUser(roles = "CUSTOMER")
    void checkoutRejectsNulInPersistedTextBeforeAccessingTheCart(String name, String note) {
        assertThatThrownBy(() -> checkout.checkout(name, "0912345678", "12 Nguyễn Trãi", note))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không hợp lệ");
    }

    @Test
    void orderCancellationRejectsNulBeforeLookingUpOrder() {
        User customer = account("CUSTOMER", UserStatus.ACTIVE, UUID.randomUUID().toString());
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken(customer.getUsername(), null, "ROLE_CUSTOMER"));

        try {
            assertThatThrownBy(() -> orderService.cancelMyOrder("ORD-20261112-999999", "Lý do\0hủy"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ký tự không hợp lệ");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void secondLoginExpiresPreviousSession() throws Exception {
        String password = UUID.randomUUID().toString();
        User admin = account("ADMIN", UserStatus.ACTIVE, password);
        var first = new MockHttpSession(application.getServletContext());
        var second = new MockHttpSession(application.getServletContext());

        for (var session : new MockHttpSession[] {first, second}) {
            mvc.perform(post("/login").session(session).with(csrf())
                    .param("username", admin.getUsername()).param("password", password))
                .andExpect(authenticated().withRoles("ADMIN"));
        }

        mvc.perform(get("/actuator/info").session(first)).andExpect(unauthenticated());
        assertThat(first.isInvalid()).isTrue();
        mvc.perform(get("/actuator/info").session(second))
            .andExpect(status().isOk()).andExpect(authenticated());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"LOCKED", "DISABLED"})
    void correctPasswordCannotAuthenticateInactiveAccount(UserStatus accountStatus) throws Exception {
        String password = UUID.randomUUID().toString();
        User analyst = account("SOC_ANALYST", accountStatus, password);
        var request = mvc.perform(post("/login").with(csrf())
                .param("username", analyst.getUsername()).param("password", password))
            .andExpect(status().is3xxRedirection()).andExpect(unauthenticated())
            .andReturn().getRequest();
        assertThat(request.getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION))
            .isInstanceOf(accountStatus == UserStatus.LOCKED ? LockedException.class : DisabledException.class);

        assertThatThrownBy(() -> desktopAuthentication.authenticate(analyst.getUsername(), password.toCharArray()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customerCannotSignInToSwingSocDashboard() {
        String password = UUID.randomUUID().toString();
        User customer = account("CUSTOMER", UserStatus.ACTIVE, password);
        assertThatThrownBy(() -> desktopAuthentication.authenticate(customer.getUsername(), password.toCharArray()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SOC");
    }

    @Test
    void serviceProxiesRequireAuthentication() {
        assertThatThrownBy(() -> alertMonitoring.details(Long.MAX_VALUE))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> incidentManagement.createIncidentFromAlert(Long.MAX_VALUE))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotReadSocDataOrMutateIncidentDirectly() {
        assertThatThrownBy(() -> alertMonitoring.details(Long.MAX_VALUE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> incidentMonitoring.details("INC-000001")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> incidentManagement.createIncidentFromAlert(Long.MAX_VALUE))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> incidentManagement.resolveIncident(Long.MAX_VALUE, "Fixture"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadSocDataButCannotWriteAnalystNotes() {
        assertThat(alertMonitoring.details(Long.MAX_VALUE)).isEmpty();
        assertThatThrownBy(() -> incidentManagement.addInvestigationNote(Long.MAX_VALUE, 1L, "Fixture"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "other-analyst", roles = "SOC_ANALYST")
    void analystCannotImpersonateAssignedNoteAuthor() {
        User assignee = account("SOC_ANALYST", UserStatus.ACTIVE, UUID.randomUUID().toString());
        SecurityAlert alert = alerts.saveAndFlush(SecurityAlert.raise(
            AlertType.BRUTE_FORCE, "198.18.91.1", Instant.now(), "security-test-" + UUID.randomUUID()));
        Long incidentId = incidentManagement.createIncidentFromAlert(alert.getId()).getId();
        incidentManagement.assignIncident(incidentId, assignee.getId());

        assertThat(alertMonitoring.details(alert.getId())).isPresent();
        assertThatThrownBy(() -> incidentManagement.addInvestigationNote(incidentId, assignee.getId(), "Fixture"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void customerCannotReadOrCancelAnotherCustomersOrder() {
        User owner = account("CUSTOMER", UserStatus.ACTIVE, UUID.randomUUID().toString());
        User other = account("CUSTOMER", UserStatus.ACTIVE, UUID.randomUUID().toString());
        Product product = products.saveAndFlush(Product.create(
            "Test product", "security-" + UUID.randomUUID(), BigDecimal.TEN, 5, null));
        Order order = Order.forUser(owner);
        order.assignOrderCode(BusinessCodes.orderCode(Instant.now(), orders.nextOrderNumber()));
        order.addItem(product, 1);
        String code = orders.saveAndFlush(order).getOrderCode();

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new TestingAuthenticationToken(owner.getUsername(), null, "ROLE_CUSTOMER"));
        SecurityContextHolder.setContext(context);
        try {
            assertThat(orderService.getMyOrderDetail(code).orderCode()).isEqualTo(code);

            context.setAuthentication(new TestingAuthenticationToken(other.getUsername(), null, "ROLE_CUSTOMER"));
            assertThatThrownBy(() -> orderService.getMyOrderDetail(code)).isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> orderService.cancelMyOrder(code, "Fixture")).isInstanceOf(SecurityException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private User account(String role, UserStatus status, String password) {
        String username = "security-" + UUID.randomUUID();
        User account = User.create(username, username + "@example.test", passwords.encode(password), "Test account");
        account.addRole(roles.findByCode(role).orElseThrow());
        if (status == UserStatus.LOCKED) {
            account.lock();
        } else if (status == UserStatus.DISABLED) {
            account.disable();
        }
        return users.saveAndFlush(account);
    }
}
