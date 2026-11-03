
package com.yak.minisoc.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

import com.yak.minisoc.entity.Product;
import com.yak.minisoc.entity.ProductStatus;
import com.yak.minisoc.repository.ProductRepository;
import com.yak.minisoc.service.CartService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.text.NumberFormat;
import java.util.Locale;

@Route("shop")
@PageTitle("Shop | Mini SOC")
@PermitAll
public class ShopView extends VerticalLayout {

    private static final int PAGE_SIZE = 10;

    private final ProductRepository productRepository;
    private final CartService cartService;

    private final Grid<Product> grid =
        new Grid<>(Product.class, false);

    private final TextField searchField =
        new TextField("Tìm kiếm sản phẩm");

    private final Paragraph pageInfo = new Paragraph();

    private final Button previousButton =
        new Button("Trang trước");

    private final Button nextButton =
        new Button("Trang sau");

    private final Button cartButton =
        new Button("🛒 Xem giỏ hàng");

    private final Button ordersButton =
        new Button("📦 Đơn hàng của tôi");

    private int currentPage = 0;
    private int totalPages = 0;

    public ShopView(
        ProductRepository productRepository,
        CartService cartService) {

        this.productRepository = productRepository;
        this.cartService = cartService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // =====================================
        // HEADER
        // =====================================

        H2 title = new H2("E-Commerce Product Store");

        boolean customer = isCustomer();

        // Cart button
        cartButton.addThemeVariants(
            ButtonVariant.LUMO_PRIMARY
        );

        cartButton.setEnabled(customer);

        cartButton.addClickListener(event ->
            getUI().ifPresent(
                ui -> ui.navigate("cart")
            )
        );

        // My Orders button
        ordersButton.addThemeVariants(
            ButtonVariant.LUMO_TERTIARY
        );

        ordersButton.setEnabled(customer);

        ordersButton.addClickListener(event ->
            getUI().ifPresent(
                ui -> ui.navigate("orders")
            )
        );

        HorizontalLayout header = new HorizontalLayout(
            title,
            ordersButton,
            cartButton
        );

        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.expand(title);

        // =====================================
        // SEARCH
        // =====================================

        searchField.setPlaceholder(
            "Nhập tên sản phẩm..."
        );

        searchField.setClearButtonVisible(true);

        Button searchButton = new Button(
            "Tìm kiếm",
            event -> {
                currentPage = 0;
                loadProducts();
            }
        );

        Button refreshButton = new Button(
            "Làm mới",
            event -> {
                searchField.clear();
                currentPage = 0;
                loadProducts();
            }
        );

        searchField.addKeyPressListener(
            com.vaadin.flow.component.Key.ENTER,
            event -> {
                currentPage = 0;
                loadProducts();
            }
        );

        HorizontalLayout filters = new HorizontalLayout(
            searchField,
            searchButton,
            refreshButton
        );

        filters.setAlignItems(Alignment.END);

        // =====================================
        // PRODUCT GRID
        // =====================================

        configureGrid();

        // =====================================
        // PAGINATION
        // =====================================

        previousButton.addClickListener(event -> {
            if (currentPage > 0) {
                currentPage--;
                loadProducts();
            }
        });

        nextButton.addClickListener(event -> {
            if (currentPage + 1 < totalPages) {
                currentPage++;
                loadProducts();
            }
        });

        HorizontalLayout pagination = new HorizontalLayout(
            previousButton,
            pageInfo,
            nextButton
        );

        pagination.setAlignItems(Alignment.CENTER);

        // =====================================
        // PAGE CONTENT
        // =====================================

        add(
            header,
            filters,
            grid,
            pagination
        );

        expand(grid);

        loadProducts();
    }

    // =====================================
    // PRODUCT GRID CONFIGURATION
    // =====================================

    private void configureGrid() {

        NumberFormat currency =
            NumberFormat.getCurrencyInstance(
                Locale.forLanguageTag("vi-VN")
            );

        grid.addColumn(Product::getId)
            .setHeader("ID")
            .setAutoWidth(true);

        grid.addColumn(Product::getName)
            .setHeader("Tên sản phẩm")
            .setAutoWidth(true);

        grid.addColumn(product ->
                currency.format(product.getPrice()))
            .setHeader("Giá")
            .setAutoWidth(true);

        grid.addColumn(Product::getStock)
            .setHeader("Tồn kho")
            .setAutoWidth(true);

        grid.addColumn(product ->
                product.getStatus().name())
            .setHeader("Trạng thái")
            .setAutoWidth(true);

        grid.addComponentColumn(product -> {

            // =================================
            // PRODUCT DETAILS
            // =================================

            Button detailButton = new Button(
                "Chi tiết",
                event -> Notification.show(
                    product.getName()
                        + " | Giá: "
                        + currency.format(
                        product.getPrice()
                    )
                        + " | Tồn kho: "
                        + product.getStock(),
                    4000,
                    Notification.Position.MIDDLE
                )
            );

            // =================================
            // ADD TO CART
            // =================================

            Button addButton = new Button(
                "Thêm vào giỏ"
            );

            addButton.setEnabled(
                product.isSellable()
                    && product.getStock() > 0
                    && isCustomer()
            );

            addButton.addClickListener(event -> {

                try {

                    int totalQuantity =
                        cartService.addToCart(
                            product.getId(),
                            1
                        );

                    Notification.show(
                        "Đã thêm vào giỏ hàng! "
                            + "Tổng số lượng: "
                            + totalQuantity,
                        3000,
                        Notification.Position.BOTTOM_START
                    );

                } catch (SecurityException
                         | IllegalArgumentException
                         | IllegalStateException ex) {

                    Notification.show(
                        ex.getMessage(),
                        4000,
                        Notification.Position.MIDDLE
                    );
                }
            });

            HorizontalLayout actions =
                new HorizontalLayout(
                    detailButton,
                    addButton
                );

            actions.setSpacing(true);
            actions.setAlignItems(
                Alignment.CENTER
            );

            return actions;

        }).setHeader("Thao tác");

        grid.setSizeFull();
    }

    // =====================================
    // CHECK CUSTOMER ROLE
    // =====================================

    private boolean isCustomer() {

        Authentication authentication =
            SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        return authentication.getAuthorities()
            .stream()
            .anyMatch(authority ->
                "ROLE_CUSTOMER".equals(
                    authority.getAuthority()
                )
            );
    }

    // =====================================
    // LOAD PRODUCTS FROM POSTGRESQL
    // =====================================

    private void loadProducts() {

        PageRequest pageable = PageRequest.of(
            currentPage,
            PAGE_SIZE,
            Sort.by("name").ascending()
        );

        String keyword =
            searchField.getValue().trim();

        Page<Product> products;

        if (keyword.isEmpty()) {

            products = productRepository.findByStatus(
                ProductStatus.ACTIVE,
                pageable
            );

        } else {

            products = productRepository
                .findByNameContainingIgnoreCaseAndStatus(
                    keyword,
                    ProductStatus.ACTIVE,
                    pageable
                );
        }

        grid.setItems(products.getContent());

        totalPages = products.getTotalPages();

        int displayedPage =
            totalPages == 0 ? 0 : currentPage + 1;

        pageInfo.setText(
            "Trang " + displayedPage
                + " / " + totalPages
                + " | Tổng sản phẩm: "
                + products.getTotalElements()
        );

        previousButton.setEnabled(
            currentPage > 0
        );

        nextButton.setEnabled(
            currentPage + 1 < totalPages
        );
    }
}
