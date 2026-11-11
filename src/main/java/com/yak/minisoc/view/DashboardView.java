
package com.yak.minisoc.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.service.DashboardAnalyticsService;
import com.yak.minisoc.service.DashboardService;
import com.yak.minisoc.service.SecurityEventSimulatorService;

import jakarta.annotation.security.RolesAllowed;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Route("")
@PageTitle("Dashboard | Mini SOC")
@RolesAllowed({"ADMIN", "SOC_ANALYST"})
public class DashboardView extends VerticalLayout {

    private final DashboardService dashboardService;
    private final SecurityEventSimulatorService simulatorService;
    private final SecurityIncidentRepository incidentRepository;
    private final DashboardAnalyticsPanel analytics;

    private final HorizontalLayout statistics = new HorizontalLayout();

    public DashboardView(
        DashboardService dashboardService,
        SecurityEventSimulatorService simulatorService,
        SecurityIncidentRepository incidentRepository,
        DashboardAnalyticsService analyticsService) {

        this.dashboardService = dashboardService;
        this.simulatorService = simulatorService;
        this.incidentRepository = incidentRepository;
        this.analytics = new DashboardAnalyticsPanel(analyticsService);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.STRETCH);

        H1 title = new H1("MINI SOC DASHBOARD");

        Paragraph subtitle = new Paragraph(
            "E-Commerce Security Monitoring System"
        );

        statistics.setWidthFull();
        statistics.setSpacing(false);

        statistics.getStyle()
            .set("display", "flex")
            .set("flex-wrap", "wrap")
            .set("gap", "16px");

        refreshDashboard();

        Button refreshButton = new Button(
            "Refresh Dashboard",
            event -> refreshDashboard()
        );

        Button eventsButton = new Button(
            "View Security Events",
            event -> getUI().ifPresent(
                ui -> ui.navigate("soc/events")
            )
        );

        Button generateButton = new Button(
            "Generate Demo Events",
            event -> generateEvents()
        );

        generateButton.getStyle()
            .set("background", "#2563eb")
            .set("color", "#ffffff")
            .set("border-radius", "8px");

        generateButton.setVisible(isAdmin());

        HorizontalLayout actions = new HorizontalLayout(
            refreshButton,
            eventsButton,
            generateButton
        );

        actions.setWidthFull();

        actions.getStyle()
            .set("flex-wrap", "wrap")
            .set("gap", "12px");
        Button incidentsButton = new Button(
            "View Security Incidents",
            event -> getUI().ifPresent(
                ui -> ui.navigate("soc/incidents")
            )
        );

        actions.add(incidentsButton);
        add(
            title,
            subtitle,
            statistics,
            new H2("Security Monitoring"),
            new Paragraph(
                "Dữ liệu thống kê được lấy từ PostgreSQL."
            ),
            actions,
            analytics
        );
    }

    private boolean isAdmin() {

        Authentication authentication =
            SecurityContextHolder.getContext()
                .getAuthentication();

        return authentication != null
            && authentication.isAuthenticated()
            && authentication.getAuthorities().stream()
            .anyMatch(authority ->
                "ROLE_ADMIN".equals(
                    authority.getAuthority()
                )
            );
    }

    private void refreshDashboard() {
        refreshStatistics();
        analytics.refresh();
    }

    private void refreshStatistics() {

        try {
            DashboardService.DashboardStats stats =
                dashboardService.getStats();

            long openIncidents =
                incidentRepository.countByStatus(
                    IncidentStatus.OPEN
                );

            long criticalIncidents =
                incidentRepository.countBySeverityAndStatus(
                    Severity.CRITICAL,
                    IncidentStatus.OPEN
                )
                    + incidentRepository.countBySeverityAndStatus(
                    Severity.CRITICAL,
                    IncidentStatus.INVESTIGATING
                );

            statistics.removeAll();

            statistics.add(
                createCard(
                    "Security Events",
                    stats.securityEvents()
                ),
                createCard(
                    "Security Incidents",
                    stats.securityIncidents()
                ),
                createCard(
                    "Active Alerts",
                    stats.activeAlerts()
                ),
                createCard(
                    "Total Users",
                    stats.users()
                ),
                createCard(
                    "Open Incidents",
                    openIncidents
                ),
                createCard(
                    "Critical Incidents",
                    criticalIncidents
                )
            );

        } catch (Exception ex) {
            Notification.show(
                "Không thể tải thống kê: " + ex.getMessage(),
                5000,
                Notification.Position.TOP_CENTER
            );
        }
    }

    private void generateEvents() {

        try {
            if (!isAdmin()) {
                throw new SecurityException(
                    "Chỉ ADMIN mới được tạo sự kiện demo."
                );
            }

            int count = simulatorService.generateDemoEvents();

            refreshDashboard();

            Notification.show(
                "Đã tạo " + count + " security events!",
                4000,
                Notification.Position.TOP_CENTER
            );

        } catch (Exception ex) {
            Notification.show(
                "Không thể tạo sự kiện: " + ex.getMessage(),
                5000,
                Notification.Position.TOP_CENTER
            );
        }
    }

    private VerticalLayout createCard(String name, long value) {

        VerticalLayout card = new VerticalLayout();

        Paragraph label = new Paragraph(name);

        label.getStyle()
            .set("color", "#cbd5e1")
            .set("font-size", "15px")
            .set("margin", "0");

        H2 number = new H2(String.valueOf(value));

        number.getStyle()
            .set("color", "#ffffff")
            .set("font-size", "32px")
            .set("font-weight", "700")
            .set("margin", "0");

        card.add(label, number);

        card.setWidth("220px");
        card.setMinWidth("180px");
        card.setPadding(true);
        card.setSpacing(true);

        card.getStyle()
            .set("background", "#172033")
            .set("color", "#ffffff")
            .set("border-radius", "12px")
            .set("box-shadow", "0 4px 12px rgba(0,0,0,0.15)")
            .set("box-sizing", "border-box");

        return card;
    }
}
