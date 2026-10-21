
package com.yak.minisoc.ui;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.service.AuthenticationService;
import com.yak.minisoc.service.DashboardService;
import com.yak.minisoc.service.SecurityEventService;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class LoginFrame extends JFrame {

    // ================= SERVICES =================

    private final AuthenticationService authenticationService;
    private final DashboardService dashboardService;
    private final SecurityEventService securityEventService;

    // ================= COMPONENTS =================

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JLabel statusLabel;

    // ================= CONSTRUCTOR =================

    public LoginFrame(
        AuthenticationService authenticationService,
        DashboardService dashboardService,
        SecurityEventService securityEventService) {

        this.authenticationService = authenticationService;
        this.dashboardService = dashboardService;
        this.securityEventService = securityEventService;

        initializeUI();
    }

    // ================= INITIALIZE UI =================

    private void initializeUI() {

        setTitle("Mini SOC - Login");
        setSize(480, 350);
        setMinimumSize(new Dimension(420, 320));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(245, 247, 250));

        mainPanel.setBorder(
            BorderFactory.createEmptyBorder(
                25, 40, 25, 40
            )
        );

        // ================= HEADER =================

        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);

        headerPanel.setLayout(
            new BoxLayout(
                headerPanel,
                BoxLayout.Y_AXIS
            )
        );

        JLabel titleLabel = new JLabel(
            "MINI SOC DASHBOARD"
        );

        titleLabel.setFont(
            new Font("Arial", Font.BOLD, 23)
        );

        titleLabel.setForeground(
            new Color(25, 45, 75)
        );

        titleLabel.setAlignmentX(
            Component.CENTER_ALIGNMENT
        );

        JLabel subtitleLabel = new JLabel(
            "E-Commerce Security Monitoring System"
        );

        subtitleLabel.setFont(
            new Font("Arial", Font.PLAIN, 12)
        );

        subtitleLabel.setForeground(
            new Color(100, 110, 125)
        );

        subtitleLabel.setAlignmentX(
            Component.CENTER_ALIGNMENT
        );

        headerPanel.add(titleLabel);
        headerPanel.add(
            Box.createVerticalStrut(8)
        );
        headerPanel.add(subtitleLabel);

        mainPanel.add(
            headerPanel,
            BorderLayout.NORTH
        );

        // ================= LOGIN FORM =================

        JPanel formPanel = new JPanel(
            new GridBagLayout()
        );

        formPanel.setOpaque(false);

        GridBagConstraints gbc =
            new GridBagConstraints();

        gbc.insets = new Insets(8, 5, 8, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel usernameLabel = new JLabel(
            "Tên đăng nhập:"
        );

        usernameLabel.setFont(
            new Font("Arial", Font.BOLD, 13)
        );

        usernameField = new JTextField(20);

        usernameField.setPreferredSize(
            new Dimension(250, 35)
        );

        JLabel passwordLabel = new JLabel(
            "Mật khẩu:"
        );

        passwordLabel.setFont(
            new Font("Arial", Font.BOLD, 13)
        );

        passwordField = new JPasswordField(20);

        passwordField.setPreferredSize(
            new Dimension(250, 35)
        );

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(usernameLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        formPanel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(passwordLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        formPanel.add(passwordField, gbc);

        mainPanel.add(
            formPanel,
            BorderLayout.CENTER
        );

        // ================= BUTTON =================

        JPanel bottomPanel = new JPanel();
        bottomPanel.setOpaque(false);

        bottomPanel.setLayout(
            new BoxLayout(
                bottomPanel,
                BoxLayout.Y_AXIS
            )
        );

        loginButton = new JButton("ĐĂNG NHẬP");

        loginButton.setFont(
            new Font("Arial", Font.BOLD, 14)
        );

        loginButton.setBackground(
            new Color(40, 90, 160)
        );

        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);

        loginButton.setAlignmentX(
            Component.CENTER_ALIGNMENT
        );

        loginButton.setMaximumSize(
            new Dimension(200, 40)
        );

        loginButton.addActionListener(
            e -> login()
        );

        statusLabel = new JLabel(" ");

        statusLabel.setFont(
            new Font("Arial", Font.PLAIN, 12)
        );

        statusLabel.setForeground(Color.RED);

        statusLabel.setAlignmentX(
            Component.CENTER_ALIGNMENT
        );

        bottomPanel.add(loginButton);

        bottomPanel.add(
            Box.createVerticalStrut(10)
        );

        bottomPanel.add(statusLabel);

        mainPanel.add(
            bottomPanel,
            BorderLayout.SOUTH
        );

        setContentPane(mainPanel);

        getRootPane().setDefaultButton(
            loginButton
        );
    }

    // ================= LOGIN =================

    private void login() {

        String username =
            usernameField.getText().trim();

        char[] password =
            passwordField.getPassword();

        if (username.isEmpty() || password.length == 0) {

            statusLabel.setForeground(Color.RED);

            statusLabel.setText(
                "Vui lòng nhập tên đăng nhập và mật khẩu."
            );

            Arrays.fill(password, '\0');
            return;
        }

        loginButton.setEnabled(false);
        usernameField.setEnabled(false);
        passwordField.setEnabled(false);

        statusLabel.setForeground(
            new Color(40, 90, 160)
        );

        statusLabel.setText("Đang xác thực...");

        SwingWorker<User, Void> worker =
            new SwingWorker<>() {

                @Override
                protected User doInBackground() {

                    try {

                        return authenticationService.authenticate(
                            username,
                            password
                        );

                    } finally {

                        Arrays.fill(password, '\0');
                    }
                }

                @Override
                protected void done() {

                    passwordField.setText("");

                    if (!isDisplayable()) {
                        return;
                    }

                    try {

                        User user = get();

                        DashboardFrame dashboardFrame =
                            new DashboardFrame(
                                user,
                                authenticationService,
                                dashboardService,
                                securityEventService
                            );

                        dashboardFrame.setVisible(true);

                        dispose();

                    } catch (InterruptedException ex) {

                        Thread.currentThread().interrupt();

                        showLoginError(
                            "Đăng nhập bị gián đoạn."
                        );

                    } catch (ExecutionException ex) {

                        showLoginError(
                            "Tên đăng nhập hoặc mật khẩu "
                                + "không đúng, hoặc tài khoản "
                                + "không hoạt động."
                        );

                    } catch (RuntimeException ex) {

                        ex.printStackTrace();

                        showLoginError(
                            "Không thể mở Dashboard."
                        );
                    }
                }
            };

        worker.execute();
    }

    // ================= LOGIN ERROR =================

    private void showLoginError(String message) {

        if (!isDisplayable()) {
            return;
        }

        loginButton.setEnabled(true);
        usernameField.setEnabled(true);
        passwordField.setEnabled(true);

        statusLabel.setForeground(Color.RED);

        statusLabel.setText(
            "<html><div style='text-align:center;'>"
                + message
                + "</div></html>"
        );

        passwordField.requestFocusInWindow();
    }
}
