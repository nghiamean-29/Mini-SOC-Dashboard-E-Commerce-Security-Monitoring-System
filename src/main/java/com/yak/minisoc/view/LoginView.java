
package com.yak.minisoc.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Đăng nhập | Mini SOC Dashboard")
@AnonymousAllowed
public class LoginView extends VerticalLayout {

    public LoginView() {

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setSpacing(true);

        H1 title = new H1("MINI SOC DASHBOARD");

        Paragraph subtitle = new Paragraph(
            "Hệ thống giám sát an ninh thương mại điện tử"
        );

        LoginForm loginForm = new LoginForm();
        loginForm.setAction("login");

        Paragraph registerText = new Paragraph(
            "Bạn chưa có tài khoản?"
        );

        Button registerButton = new Button(
            "Đăng ký tài khoản",
            event -> getUI().ifPresent(
                ui -> ui.navigate("register")
            )
        );

        add(
            title,
            subtitle,
            loginForm,
            registerText,
            registerButton
        );
    }
}
