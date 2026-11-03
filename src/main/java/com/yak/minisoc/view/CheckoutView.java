
package com.yak.minisoc.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.yak.minisoc.service.CartService;
import com.yak.minisoc.service.CheckoutService;

import jakarta.annotation.security.RolesAllowed;

import java.text.NumberFormat;
import java.util.Locale;

@Route("checkout")
@PageTitle("Đặt hàng | Mini SOC")
@RolesAllowed("CUSTOMER")
public class CheckoutView extends VerticalLayout {

    private final CheckoutService checkoutService;
    private final CartService cartService;

    public CheckoutView(
        CheckoutService checkoutService,
        CartService cartService) {

        this.checkoutService = checkoutService;
        this.cartService = cartService;

        setWidthFull();
        setMaxWidth("650px");
        setPadding(true);
        setSpacing(true);

        H2 title = new H2("XÁC NHẬN ĐẶT HÀNG");

        TextField name = new TextField("Tên người nhận");
        name.setWidthFull();
        name.setRequired(true);
        name.setMaxLength(120);

        TextField phone = new TextField("Số điện thoại");
        phone.setWidthFull();
        phone.setRequired(true);
        phone.setMaxLength(20);

        TextArea address = new TextArea("Địa chỉ giao hàng");
        address.setWidthFull();
        address.setRequired(true);
        address.setMaxLength(255);

        TextArea note = new TextArea("Ghi chú");
        note.setWidthFull();
        note.setMaxLength(500);

        H3 total = new H3();

        CartService.CartSummary summary =
            cartService.getMyCart();

        NumberFormat money = NumberFormat.getCurrencyInstance(
            Locale.forLanguageTag("vi-VN")
        );

        total.setText(
            "Tổng thanh toán: "
                + money.format(summary.totalPrice())
        );

        Paragraph quantity = new Paragraph(
            "Số lượng sản phẩm: "
                + summary.totalQuantity()
        );

        Button confirm = new Button("Đặt hàng");

        confirm.addThemeVariants(
            ButtonVariant.LUMO_PRIMARY
        );

        confirm.setEnabled(!summary.items().isEmpty());

        confirm.addClickListener(event -> {

            confirm.setEnabled(false);

            try {

                CheckoutService.CheckoutResult result =
                    checkoutService.checkout(
                        name.getValue(),
                        phone.getValue(),
                        address.getValue(),
                        note.getValue()
                    );

                Notification.show(
                    "Đặt hàng thành công! Mã đơn: "
                        + result.orderCode(),
                    5000,
                    Notification.Position.TOP_CENTER
                );

                getUI().ifPresent(
                    ui -> ui.navigate("shop")
                );

            } catch (Exception ex) {

                Notification.show(
                    "Không thể đặt hàng: "
                        + ex.getMessage(),
                    5000,
                    Notification.Position.MIDDLE
                );

                confirm.setEnabled(true);
            }
        });

        Button back = new Button(
            "Quay lại giỏ hàng",
            event -> getUI().ifPresent(
                ui -> ui.navigate("cart")
            )
        );

        add(
            title,
            name,
            phone,
            address,
            note,
            quantity,
            total,
            confirm,
            back
        );
    }
}
