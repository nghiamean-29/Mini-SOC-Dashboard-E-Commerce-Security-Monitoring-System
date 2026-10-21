package com.yak.minisoc.ui;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.service.AuthenticationService;
import com.yak.minisoc.service.DashboardService;
import com.yak.minisoc.service.SecurityEventService;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;

public class DashboardFrame extends JFrame {

    // ================= SERVICES =================

    private final User currentUser;
    private final AuthenticationService authenticationService;
    private final DashboardService dashboardService;
    private final SecurityEventService securityEventService;

    // ================= UI COMPONENTS =================

    private JPanel contentPanel;

    private JLabel eventsValue;
    private JLabel incidentsValue;
    private JLabel alertsValue;
    private JLabel usersValue;

    // ================= CONSTRUCTOR =================

    public DashboardFrame(
        User currentUser,
        AuthenticationService authenticationService,
        DashboardService dashboardService,
        SecurityEventService securityEventService) {

        this.currentUser = currentUser;
        this.authenticationService = authenticationService;
        this.dashboardService = dashboardService;
        this.securityEventService = securityEventService;

        initializeUI();
        loadDashboardStats();
    }

    // ================= INITIALIZE UI =================

    private void initializeUI() {

        setTitle("Mini SOC Dashboard");
        setSize(1100, 700);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout(new BorderLayout());

        add(createHeader(), BorderLayout.NORTH);
        add(createSidebar(), BorderLayout.WEST);

        contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(
            createDashboard(),
            BorderLayout.CENTER
        );

        add(contentPanel, BorderLayout.CENTER);
    }

    // ================= HEADER =================

    private JPanel createHeader() {

        JPanel header = new JPanel(new BorderLayout());

        header.setBackground(new Color(25, 35, 55));

        header.setBorder(
            BorderFactory.createEmptyBorder(
                18, 25, 18, 25
            )
        );

        JLabel title = new JLabel("MINI SOC DASHBOARD");

        title.setForeground(Color.WHITE);
        title.setFont(
            new Font("Arial", Font.BOLD, 22)
        );

        JLabel userLabel = new JLabel(
            "User: " + currentUser.getUsername()
        );

        userLabel.setForeground(Color.WHITE);

        header.add(title, BorderLayout.WEST);
        header.add(userLabel, BorderLayout.EAST);

        return header;
    }

    // ================= SIDEBAR =================

    private JPanel createSidebar() {

        JPanel sidebar = new JPanel();

        sidebar.setLayout(
            new BoxLayout(sidebar, BoxLayout.Y_AXIS)
        );

        sidebar.setPreferredSize(
            new Dimension(220, 0)
        );

        sidebar.setBackground(
            new Color(35, 45, 65)
        );

        sidebar.setBorder(
            BorderFactory.createEmptyBorder(
                20, 10, 20, 10
            )
        );

        addMenuButton(sidebar, "Tổng quan");
        addMenuButton(sidebar, "Security Events");
        addMenuButton(sidebar, "Security Incidents");
        addMenuButton(sidebar, "Quản lý người dùng");
        addMenuButton(sidebar, "Audit Logs");

        sidebar.add(Box.createVerticalGlue());

        JButton logoutButton = new JButton("Đăng xuất");

        logoutButton.setAlignmentX(
            Component.CENTER_ALIGNMENT
        );

        logoutButton.setMaximumSize(
            new Dimension(190, 40)
        );

        logoutButton.addActionListener(
            e -> logout()
        );

        sidebar.add(logoutButton);

        return sidebar;
    }

    private void addMenuButton(
        JPanel sidebar,
        String text) {

        JButton button = new JButton(text);

        button.setAlignmentX(
            Component.CENTER_ALIGNMENT
        );

        button.setMaximumSize(
            new Dimension(190, 45)
        );

        button.setFocusPainted(false);

        button.addActionListener(e -> {

            switch (text) {

                case "Tổng quan" ->
                    showOverview();

                case "Security Events" ->
                    showSecurityEvents();

                default ->
                    JOptionPane.showMessageDialog(
                        this,
                        "Chức năng sẽ được triển khai "
                            + "ở bước tiếp theo."
                    );
            }
        });

        sidebar.add(button);

        sidebar.add(
            Box.createVerticalStrut(12)
        );
    }

    // ================= OVERVIEW =================

    private JPanel createDashboard() {

        JPanel dashboard = new JPanel(
            new BorderLayout(15, 20)
        );

        dashboard.setBorder(
            BorderFactory.createEmptyBorder(
                25, 25, 25, 25
            )
        );

        JLabel heading = new JLabel(
            "Tổng quan an ninh hệ thống"
        );

        heading.setFont(
            new Font("Arial", Font.BOLD, 24)
        );

        dashboard.add(
            heading,
            BorderLayout.NORTH
        );

        JPanel statistics = new JPanel(
            new GridLayout(2, 2, 20, 20)
        );

        statistics.add(
            createStatCard("Security Events", 0)
        );

        statistics.add(
            createStatCard("Security Incidents", 1)
        );

        statistics.add(
            createStatCard("Active Alerts", 2)
        );

        statistics.add(
            createStatCard("Users", 3)
        );

        dashboard.add(
            statistics,
            BorderLayout.CENTER
        );

        JLabel footer = new JLabel(
            "Mini SOC - E-Commerce Security Monitoring System",
            SwingConstants.CENTER
        );

        dashboard.add(
            footer,
            BorderLayout.SOUTH
        );

        return dashboard;
    }

    // ================= STAT CARDS =================

    private JPanel createStatCard(
        String title,
        int type) {

        JPanel card = new JPanel(
            new BorderLayout()
        );

        card.setBackground(Color.WHITE);

        card.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                    new Color(210, 215, 225)
                ),
                BorderFactory.createEmptyBorder(
                    20, 20, 20, 20
                )
            )
        );

        JLabel nameLabel = new JLabel(title);

        nameLabel.setFont(
            new Font("Arial", Font.BOLD, 17)
        );

        JLabel valueLabel = new JLabel(
            "Đang tải..."
        );

        valueLabel.setFont(
            new Font("Arial", Font.BOLD, 32)
        );

        valueLabel.setForeground(
            new Color(40, 90, 160)
        );

        switch (type) {

            case 0 ->
                eventsValue = valueLabel;

            case 1 ->
                incidentsValue = valueLabel;

            case 2 ->
                alertsValue = valueLabel;

            case 3 ->
                usersValue = valueLabel;

            default ->
                throw new IllegalArgumentException(
                    "Loại thống kê không hợp lệ: " + type
                );
        }

        card.add(
            nameLabel,
            BorderLayout.NORTH
        );

        card.add(
            valueLabel,
            BorderLayout.CENTER
        );

        return card;
    }

    // ================= LOAD DASHBOARD DATA =================

    private void loadDashboardStats() {

        if (eventsValue == null) {
            return;
        }

        // Giữ tham chiếu tới đúng các nhãn đang tải.
        JLabel targetEvents = eventsValue;
        JLabel targetIncidents = incidentsValue;
        JLabel targetAlerts = alertsValue;
        JLabel targetUsers = usersValue;

        targetEvents.setText("Đang tải...");
        targetIncidents.setText("Đang tải...");
        targetAlerts.setText("Đang tải...");
        targetUsers.setText("Đang tải...");

        SwingWorker<DashboardService.DashboardStats, Void> worker =
            new SwingWorker<>() {

                @Override
                protected DashboardService.DashboardStats
                doInBackground() {

                    return dashboardService.getStats();
                }

                @Override
                protected void done() {

                    if (!isDisplayable()) {
                        return;
                    }

                    // Không cập nhật một màn hình đã bị thay thế.
                    if (eventsValue != targetEvents) {
                        return;
                    }

                    try {

                        DashboardService.DashboardStats stats =
                            get();

                        targetEvents.setText(
                            String.valueOf(
                                stats.securityEvents()
                            )
                        );

                        targetIncidents.setText(
                            String.valueOf(
                                stats.securityIncidents()
                            )
                        );

                        targetAlerts.setText(
                            String.valueOf(
                                stats.activeAlerts()
                            )
                        );

                        targetUsers.setText(
                            String.valueOf(
                                stats.users()
                            )
                        );

                    } catch (InterruptedException ex) {

                        Thread.currentThread().interrupt();
                        showStatsError(
                            targetEvents,
                            targetIncidents,
                            targetAlerts,
                            targetUsers
                        );

                    } catch (ExecutionException ex) {

                        ex.printStackTrace();

                        showStatsError(
                            targetEvents,
                            targetIncidents,
                            targetAlerts,
                            targetUsers
                        );
                    }
                }
            };

        worker.execute();
    }

    private void showStatsError(
        JLabel events,
        JLabel incidents,
        JLabel alerts,
        JLabel users) {

        events.setText("Lỗi tải");
        incidents.setText("Lỗi tải");
        alerts.setText("Lỗi tải");
        users.setText("Lỗi tải");

        JOptionPane.showMessageDialog(
            this,
            "Không thể tải dữ liệu Dashboard.",
            "Lỗi dữ liệu",
            JOptionPane.ERROR_MESSAGE
        );
    }

    // ================= NAVIGATION =================

    private void showOverview() {

        contentPanel.removeAll();

        contentPanel.add(
            createDashboard(),
            BorderLayout.CENTER
        );

        contentPanel.revalidate();
        contentPanel.repaint();

        loadDashboardStats();
    }

    private void showSecurityEvents() {

        contentPanel.removeAll();

        SecurityEventsPanel eventsPanel =
            new SecurityEventsPanel(
                securityEventService,
                null,  // Chua ket noi simulator service
                false  // Tam khoa nut demo den khi co kiem tra quyen ADMIN
            );

        contentPanel.add(
            eventsPanel,
            BorderLayout.CENTER
        );

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    // ================= LOGOUT =================

    private void logout() {

        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Bạn có muốn đăng xuất?",
            "Xác nhận đăng xuất",
            JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {

            LoginFrame loginFrame = new LoginFrame(
                authenticationService,
                dashboardService,
                securityEventService
            );

            loginFrame.setVisible(true);

            dispose();
        }
    }
}
