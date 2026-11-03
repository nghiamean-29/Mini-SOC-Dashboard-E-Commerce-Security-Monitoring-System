
package com.yak.minisoc.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.yak.minisoc.service.CartService;
import com.yak.minisoc.service.CartService.CartLine;
import com.yak.minisoc.service.CartService.CartSummary;

import jakarta.annotation.security.RolesAllowed;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Route("cart")
@PageTitle("Giỏ hàng | Mini SOC")
@RolesAllowed("CUSTOMER")
public class CartView extends VerticalLayout {

    private final CartService cartService;

    private final Grid<CartLine> grid =
        new Grid<>(CartLine.class, false);

    private final H3 totalLabel = new H3();

    private final Paragraph quantityLabel = new Paragraph();

    private final Button checkoutButton =
        new Button("🛒 Tiến hành đặt hàng");

    public CartView(CartService cartService) {

        this.cartService = cartService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // ============================
        // HEADER
        // ============================

        H2 title = new H2("GIỎ HÀNG CỦA BẠN");

        Button backButton = new Button(
            "← Tiếp tục mua sắm",
            event -> getUI().ifPresent(
                ui -> ui.navigate("shop")
            )
        );

        HorizontalLayout header = new HorizontalLayout(
            title,
            backButton
        );

        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.expand(title);

        // ============================
        // CHECKOUT BUTTON
        // ============================

        checkoutButton.addThemeVariants(
            ButtonVariant.LUMO_PRIMARY
        );

        checkoutButton.addClickListener(event ->
            getUI().ifPresent(
                ui -> ui.navigate("checkout")
            )
        );

        // ============================
        // PRODUCT GRID
        // ============================

        configureGrid();

        // ============================
        // CART SUMMARY
        // ============================

        HorizontalLayout summaryLayout =
            new HorizontalLayout(
                quantityLabel,
                totalLabel
            );

        summaryLayout.setWidthFull();
        summaryLayout.setAlignItems(
            Alignment.CENTER
        );

        summaryLayout.expand(quantityLabel);

        // ============================
        // PAGE CONTENT
        // ============================

        add(
            header,
            grid,
            summaryLayout,
            checkoutButton
        );

        refreshCart();
    }

    // =================================
    // CONFIGURE CART GRID
    // =================================

    private void configureGrid() {

        grid.addColumn(CartLine::productName)
            .setHeader("Sản phẩm")
            .setAutoWidth(true);

        grid.addColumn(item ->
                formatMoney(item.unitPrice()))
            .setHeader("Đơn giá");

        grid.addColumn(CartLine::quantity)
            .setHeader("Số lượng");

        grid.addColumn(item ->
                formatMoney(item.lineTotal()))
            .setHeader("Thành tiền");

        grid.addComponentColumn(item -> {

            // Decrease quantity
            Button decrease = new Button(
                "−",
                event -> changeQuantity(
                    item,
                    item.quantity() - 1
                )
            );

            // Increase quantity
            Button increase = new Button(
                "+",
                event -> changeQuantity(
                    item,
                    item.quantity() + 1
                )
            );

            increase.setEnabled(
                item.quantity() < item.stock()
            );

            // Remove product
            Button remove = new Button(
                "Xóa",
                event -> {
                    try {

                        cartService.removeFromCart(
                            item.productId()
                        );

                        Notification.show(
                            "Đã xóa sản phẩm khỏi giỏ hàng."
                        );

                        refreshCart();

                    } catch (Exception ex) {
                        showError(ex);
                    }
                }
            );

            remove.addThemeVariants(
                ButtonVariant.LUMO_ERROR
            );

            HorizontalLayout actions =
                new HorizontalLayout(
                    decrease,
                    increase,
                    remove
                );

            actions.setSpacing(true);
            actions.setAlignItems(
                Alignment.CENTER
            );

            return actions;

        }).setHeader("Thao tác");

        grid.setWidthFull();
        grid.setAllRowsVisible(true);
    }

    // =================================
    // UPDATE QUANTITY
    // =================================

    private void changeQuantity(
        CartLine item,
        int quantity) {

        try {

            cartService.updateQuantity(
                item.productId(),
                quantity
            );

            Notification.show(
                quantity <= 0
                    ? "Đã xóa sản phẩm."
                    : "Đã cập nhật giỏ hàng."
            );

            refreshCart();

        } catch (Exception ex) {
            showError(ex);
        }
    }

    // =================================
    // REFRESH CART
    // =================================

    private void refreshCart() {

        try {

            CartSummary summary =
                cartService.getMyCart();

            grid.setItems(summary.items());

            quantityLabel.setText(
                "Tổng số lượng: "
                    + summary.totalQuantity()
            );

            totalLabel.setText(
                "Tổng tiền: "
                    + formatMoney(
                    summary.totalPrice()
                )
            );

            // Disable checkout if cart is empty
            checkoutButton.setEnabled(
                !summary.items().isEmpty()
            );

        } catch (Exception ex) {

            checkoutButton.setEnabled(false);
            showError(ex);
        }
    }

    // =================================
    // FORMAT VND
    // =================================

    private String formatMoney(
        BigDecimal value) {

        NumberFormat format =
            NumberFormat.getNumberInstance(
                Locale.forLanguageTag("vi-VN")
            );

        format.setMaximumFractionDigits(0);

        return format.format(value) + " đ";
    }

    // =================================
    // ERROR HANDLING
    // =================================

    private void showError(Exception ex) {

        Notification.show(
            "Lỗi giỏ hàng: " + ex.getMessage(),
            4000,
            Notification.Position.MIDDLE
        );
    }
}
