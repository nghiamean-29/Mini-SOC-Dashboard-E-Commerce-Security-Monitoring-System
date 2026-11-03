
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

    private final HorizontalLayout statistics =
        new HorizontalLayout();

    public DashboardView(
        DashboardService dashboardService,
        SecurityEventSimulatorService simulatorService) {

        this.dashboardService = dashboardService;
        this.simulatorService = simulatorService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.STRETCH);

        H1 title = new H1("MINI SOC DASHBOARD");

        Paragraph subtitle = new Paragraph(
            "E-Commerce Security Monitoring System"
        );

        statistics.setWidthFull();
        statistics.setSpacing(true);

        refreshStatistics();

        Button generateButton = new Button(
            "Generate Demo Events",
            event -> generateEvents()
        );

        generateButton.getStyle()
            .set("background", "#2563eb")
            .set("color", "#ffffff")
            .set("border-radius", "8px");

        // Only ADMIN can generate demo events
        generateButton.setVisible(isAdmin());

        Button eventsButton = new Button(
            "View Security Events",
            event -> getUI().ifPresent(
                ui -> ui.navigate("soc/events")
            )
        );

        add(
            title,
            subtitle,
            statistics,
            new H2("Security Monitoring"),
            new Paragraph(
                "Dữ liệu thống kê được lấy từ PostgreSQL."
            ),
            eventsButton,
            generateButton
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

    private void refreshStatistics() {

        DashboardService.DashboardStats stats =
            dashboardService.getStats();

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
            )
        );
    }

    private void generateEvents() {

        try {

            if (!isAdmin()) {
                throw new SecurityException(
                    "Chỉ ADMIN mới được tạo sự kiện demo."
                );
            }

            int count =
                simulatorService.generateDemoEvents();

            refreshStatistics();

            Notification.show(
                "Đã tạo " + count + " security events!",
                4000,
                Notification.Position.TOP_CENTER
            );

        } catch (Exception ex) {

            Notification.show(
                "Không thể tạo sự kiện: "
                    + ex.getMessage(),
                5000,
                Notification.Position.TOP_CENTER
            );
        }
    }

    private VerticalLayout createCard(
        String name,
        long value) {

        VerticalLayout card = new VerticalLayout();

        card.add(
            new Paragraph(name),
            new H2(String.valueOf(value))
        );

        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);

        card.getStyle()
            .set("background", "#172033")
            .set("color", "#ffffff")
            .set("border-radius", "12px");

        return card;
    }
}
