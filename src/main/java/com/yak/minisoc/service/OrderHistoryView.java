
package com.yak.minisoc.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.yak.minisoc.service.OrderService;
import com.yak.minisoc.service.OrderService.OrderDetail;
import com.yak.minisoc.service.OrderService.OrderLine;
import com.yak.minisoc.service.OrderService.OrderSummary;

import jakarta.annotation.security.RolesAllowed;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Route("orders")
@PageTitle("Lịch sử đơn hàng | Mini SOC")
@RolesAllowed("CUSTOMER")
public class OrderHistoryView extends VerticalLayout {

    private static final int PAGE_SIZE = 10;

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final OrderService orderService;

    private final Grid<OrderSummary> grid =
        new Grid<>(OrderSummary.class, false);

    private final Paragraph pageInfo = new Paragraph();

    private final Button previousButton =
        new Button("Trang trước");

    private final Button nextButton =
        new Button("Trang sau");

    private int currentPage = 0;
    private int totalPages = 0;

    public OrderHistoryView(OrderService orderService) {

        this.orderService = orderService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 title = new H2("LỊCH SỬ ĐƠN HÀNG");

        Button shopButton = new Button(
            "Tiếp tục mua sắm",
            event -> getUI().ifPresent(
                ui -> ui.navigate("shop")
            )
        );

        configureGrid();

        previousButton.addClickListener(event -> {
            if (currentPage > 0) {
                currentPage--;
                loadOrders();
            }
        });

        nextButton.addClickListener(event -> {
            if (currentPage + 1 < totalPages) {
                currentPage++;
                loadOrders();
            }
        });

        HorizontalLayout pagination = new HorizontalLayout(
            previousButton,
            pageInfo,
            nextButton
        );

        pagination.setAlignItems(Alignment.CENTER);

        add(title, shopButton, grid, pagination);
        expand(grid);

        loadOrders();
    }

    // ==========================================
    // CONFIGURE ORDER GRID
    // ==========================================

    private void configureGrid() {

        grid.addColumn(OrderSummary::orderCode)
            .setHeader("Mã đơn hàng")
            .setAutoWidth(true);

        grid.addColumn(order ->
                DATE_FORMAT.format(order.createdAt()))
            .setHeader("Ngày đặt")
            .setAutoWidth(true);

        grid.addColumn(order ->
                formatMoney(order.totalAmount()))
            .setHeader("Tổng tiền")
            .setAutoWidth(true);

        grid.addColumn(OrderSummary::status)
            .setHeader("Trạng thái")
            .setAutoWidth(true);

        grid.addComponentColumn(order -> {

            Button detailButton = new Button(
                "Xem chi tiết",
                event -> showOrderDetail(order.orderCode())
            );

            HorizontalLayout actions =
                new HorizontalLayout(detailButton);

            actions.setSpacing(true);
            actions.setAlignItems(Alignment.CENTER);

            if (isCancellable(order.status())) {

                Button cancelButton = new Button(
                    "Hủy đơn",
                    event -> showCancelDialog(order.orderCode())
                );

                cancelButton.addThemeVariants(
                    ButtonVariant.LUMO_ERROR,
                    ButtonVariant.LUMO_TERTIARY
                );

                actions.add(cancelButton);
            }

            return actions;

        }).setHeader("Thao tác").setAutoWidth(true);

        grid.setWidthFull();
    }

    // ==========================================
    // LOAD ORDER HISTORY
    // ==========================================

    private void loadOrders() {

        try {

            Page<OrderSummary> orders =
                orderService.getMyOrders(
                    currentPage,
                    PAGE_SIZE
                );

            // If the current page no longer exists,
            // return to the last available page.
            if (orders.getTotalPages() > 0
                && currentPage >= orders.getTotalPages()) {

                currentPage = orders.getTotalPages() - 1;

                orders = orderService.getMyOrders(
                    currentPage,
                    PAGE_SIZE
                );
            }

            grid.setItems(orders.getContent());

            totalPages = orders.getTotalPages();

            int displayedPage =
                totalPages == 0 ? 0 : currentPage + 1;

            pageInfo.setText(
                "Trang " + displayedPage
                    + " / " + totalPages
                    + " | Tổng đơn hàng: "
                    + orders.getTotalElements()
            );

            previousButton.setEnabled(currentPage > 0);

            nextButton.setEnabled(
                currentPage + 1 < totalPages
            );

        } catch (Exception ex) {

            showError(
                "Không thể tải đơn hàng: " + ex.getMessage()
            );
        }
    }

    // ==========================================
    // SHOW ORDER DETAIL
    // ==========================================

    private void showOrderDetail(String orderCode) {

        try {

            OrderDetail order =
                orderService.getMyOrderDetail(orderCode);

            Dialog dialog = new Dialog();

            dialog.setHeaderTitle(
                "Chi tiết đơn hàng: " + order.orderCode()
            );

            dialog.setWidth("650px");
            dialog.setMaxWidth("95vw");

            VerticalLayout content = new VerticalLayout();
            content.setPadding(false);

            content.add(
                new Paragraph(
                    "Trạng thái: " + order.status()
                ),
                new Paragraph(
                    "Ngày đặt: "
                        + DATE_FORMAT.format(
                        order.createdAt()
                    )
                ),
                new Paragraph(
                    "Người nhận: "
                        + order.shippingName()
                ),
                new Paragraph(
                    "Số điện thoại: "
                        + order.shippingPhone()
                ),
                new Paragraph(
                    "Địa chỉ: "
                        + order.shippingAddress()
                )
            );

            Grid<OrderLine> itemGrid =
                new Grid<>(OrderLine.class, false);

            itemGrid.addColumn(OrderLine::productName)
                .setHeader("Sản phẩm");

            itemGrid.addColumn(OrderLine::quantity)
                .setHeader("Số lượng");

            itemGrid.addColumn(item ->
                    formatMoney(item.unitPrice()))
                .setHeader("Đơn giá");

            itemGrid.addColumn(item ->
                    formatMoney(item.lineTotal()))
                .setHeader("Thành tiền");

            itemGrid.setItems(order.items());
            itemGrid.setAllRowsVisible(true);
            itemGrid.setWidthFull();

            content.add(
                itemGrid,
                new H3(
                    "Tổng tiền: "
                        + formatMoney(order.totalAmount())
                )
            );

            if (order.note() != null
                && !order.note().isBlank()) {

                content.add(
                    new Paragraph(
                        "Ghi chú: " + order.note()
                    )
                );
            }

            Button closeButton = new Button(
                "Đóng",
                event -> dialog.close()
            );

            dialog.add(content);
            dialog.getFooter().add(closeButton);

            dialog.open();

        } catch (Exception ex) {

            showError(
                "Không thể xem chi tiết: " + ex.getMessage()
            );
        }
    }

    // ==========================================
    // SHOW CANCEL CONFIRMATION
    // ==========================================

    private void showCancelDialog(String orderCode) {

        Dialog dialog = new Dialog();

        dialog.setHeaderTitle("Xác nhận hủy đơn hàng");
        dialog.setWidth("450px");
        dialog.setMaxWidth("95vw");

        Paragraph warning = new Paragraph(
            "Bạn có chắc chắn muốn hủy đơn hàng "
                + orderCode + "?"
        );

        Paragraph information = new Paragraph(
            "Sau khi hủy thành công, đơn hàng sẽ "
                + "chuyển sang CANCELLED và số lượng "
                + "sản phẩm sẽ được hoàn lại kho."
        );

        TextArea reasonField =
            new TextArea("Lý do hủy đơn hàng");

        reasonField.setPlaceholder(
            "Ví dụ: Tôi muốn thay đổi sản phẩm."
        );

        reasonField.setRequiredIndicatorVisible(true);
        reasonField.setMaxLength(255);
        reasonField.setWidthFull();

        VerticalLayout content = new VerticalLayout(
            warning,
            information,
            reasonField
        );

        content.setPadding(false);
        content.setWidthFull();

        Button closeButton = new Button(
            "Không hủy",
            event -> dialog.close()
        );

        Button confirmButton = new Button(
            "Xác nhận hủy"
        );

        confirmButton.addThemeVariants(
            ButtonVariant.LUMO_PRIMARY,
            ButtonVariant.LUMO_ERROR
        );

        confirmButton.addClickListener(event -> {

            String reason = reasonField.getValue().trim();

            if (reason.isBlank()) {

                reasonField.setInvalid(true);
                reasonField.setErrorMessage(
                    "Vui lòng nhập lý do hủy đơn."
                );

                return;
            }

            reasonField.setInvalid(false);

            // Prevent repeated clicks while processing.
            confirmButton.setEnabled(false);

            try {

                orderService.cancelMyOrder(
                    orderCode,
                    reason
                );

                dialog.close();

                Notification.show(
                    "Đã hủy đơn hàng và hoàn tồn kho thành công.",
                    4000,
                    Notification.Position.MIDDLE
                );

                loadOrders();

            } catch (Exception ex) {

                showError(
                    "Không thể hủy đơn hàng: "
                        + ex.getMessage()
                );

            } finally {

                confirmButton.setEnabled(true);
            }
        });

        dialog.add(content);

        dialog.getFooter().add(
            closeButton,
            confirmButton
        );

        dialog.open();
    }

    // ==========================================
    // ORDER STATUS RULE
    // ==========================================

    private boolean isCancellable(String status) {

        return "PENDING".equals(status)
            || "CONFIRMED".equals(status);
    }

    // ==========================================
    // ERROR NOTIFICATION
    // ==========================================

    private void showError(String message) {

        Notification.show(
            message,
            4000,
            Notification.Position.MIDDLE
        );
    }

    // ==========================================
    // FORMAT VND
    // ==========================================

    private String formatMoney(BigDecimal value) {

        NumberFormat format =
            NumberFormat.getNumberInstance(
                Locale.forLanguageTag("vi-VN")
            );

        format.setMaximumFractionDigits(0);

        return format.format(value) + " đ";
    }
}
