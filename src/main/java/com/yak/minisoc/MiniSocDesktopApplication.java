
package com.yak.minisoc;

import com.yak.minisoc.service.AuthenticationService;
import com.yak.minisoc.service.DashboardService;
import com.yak.minisoc.service.SecurityEventService;
import com.yak.minisoc.ui.LoginFrame;

import java.awt.GraphicsEnvironment;

import javax.swing.SwingUtilities;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

public class MiniSocDesktopApplication {

    public static void main(String[] args) {

        // ==========================================
        // 1. KIEM TRA MOI TRUONG DO HOA
        // ==========================================

        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException(
                "Mini SOC Desktop requires a graphical environment"
            );
        }

        // ==========================================
        // 2. KHOI DONG SPRING BOOT
        // ==========================================

        SpringApplication app =
            new SpringApplication(MiniSocApplication.class);

        // Chay ung dung Desktop, khong khoi dong Tomcat.
        app.setWebApplicationType(WebApplicationType.NONE);

        System.out.println(
            ">>> BAT DAU KHOI DONG SPRING BOOT"
        );

        // Khoi dong Spring Boot va ket noi PostgreSQL.
        ConfigurableApplicationContext context = app.run(args);

        // ==========================================
        // 3. LAY CAC SERVICE TU SPRING CONTEXT
        // ==========================================

        AuthenticationService authenticationService =
            context.getBean(AuthenticationService.class);

        DashboardService dashboardService =
            context.getBean(DashboardService.class);

        SecurityEventService securityEventService =
            context.getBean(SecurityEventService.class);

        System.out.println(
            ">>> SPRING BOOT DA KHOI DONG THANH CONG"
        );

        // ==========================================
        // 4. MO GIAO DIEN JAVA SWING
        // ==========================================

        SwingUtilities.invokeLater(() -> {

            System.out.println(
                ">>> DANG MO GIAO DIEN SWING"
            );

            try {

                LoginFrame loginFrame = new LoginFrame(
                    authenticationService,
                    dashboardService,
                    securityEventService
                );

                loginFrame.setVisible(true);

                System.out.println(
                    ">>> GIAO DIEN DANG NHAP DA DUOC MO"
                );

            } catch (Exception ex) {

                System.err.println(
                    ">>> LOI KHI MO GIAO DIEN SWING"
                );

                ex.printStackTrace();

                context.close();

                System.exit(1);
            }
        });
    }
}
