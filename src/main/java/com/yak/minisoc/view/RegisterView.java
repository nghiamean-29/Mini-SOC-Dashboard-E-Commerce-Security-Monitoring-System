
package com.yak.minisoc.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.yak.minisoc.service.RegistrationService;
import org.springframework.dao.DataIntegrityViolationException;

@Route("register")
@PageTitle("Đăng ký | Mini SOC Dashboard")
@AnonymousAllowed
public class RegisterView extends VerticalLayout {

    public RegisterView(RegistrationService service) {

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setSpacing(true);

        H2 title = new H2("ĐĂNG KÝ TÀI KHOẢN");

        TextField username = new TextField("Tên đăng nhập");
        username.setRequired(true);
        username.setMaxLength(30);

        EmailField email = new EmailField("Email");
        email.setRequiredIndicatorVisible(true);
        email.setMaxLength(120);

        PasswordField password =
            new PasswordField("Mật khẩu");
        password.setRequired(true);

        PasswordField confirmPassword =
            new PasswordField("Xác nhận mật khẩu");
        confirmPassword.setRequired(true);

        Button registerButton = new Button(
            "Đăng ký",
            event -> {
                if (!password.getValue().equals(
                    confirmPassword.getValue())) {
                    Notification.show(
                        "Mật khẩu xác nhận không khớp."
                    );
                    return;
                }

                try {
                    service.register(
                        username.getValue(),
                        email.getValue(),
                        password.getValue()
                    );

                    Notification.show(
                        "Đăng ký thành công!"
                    );

                    UI.getCurrent().navigate("login");

                } catch (IllegalArgumentException ex) {
                    Notification.show(ex.getMessage());

                } catch (DataIntegrityViolationException ex) {
                    Notification.show(
                        "Tên đăng nhập hoặc email đã tồn tại."
                    );

                } catch (Exception ex) {
                    Notification.show(
                        "Không thể đăng ký. Vui lòng thử lại."
                    );
                }
            }
        );

        Button backButton = new Button(
            "Quay lại đăng nhập",
            event -> UI.getCurrent().navigate("login")
        );

        add(
            title,
            username,
            email,
            password,
            confirmPassword,
            registerButton,
            backButton
        );
    }
}
