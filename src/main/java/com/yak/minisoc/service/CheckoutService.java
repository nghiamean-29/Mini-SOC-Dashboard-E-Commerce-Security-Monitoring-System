
package com.yak.minisoc.service;

import com.yak.minisoc.entity.*;
import com.yak.minisoc.repository.*;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CheckoutService {

    private static final ZoneId BUSINESS_ZONE =
        ZoneId.of("Asia/Ho_Chi_Minh");

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final EntityManager entityManager;

    // Security Event Collector
    private final SecurityEventService securityEventService;

    // ==========================================
    // DTO - CHECKOUT RESULT
    // ==========================================

    public record CheckoutResult(
        String orderCode,
        BigDecimal totalAmount,
        int totalQuantity
    ) {}

    // ==========================================
    // CONSTRUCTOR
    // ==========================================

    public CheckoutService(
        CartRepository cartRepository,
        ProductRepository productRepository,
        UserRepository userRepository,
        OrderRepository orderRepository,
        EntityManager entityManager,
        SecurityEventService securityEventService) {

        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.entityManager = entityManager;
        this.securityEventService = securityEventService;
    }

    // ==========================================
    // CHECKOUT
    // ==========================================

    @Transactional
    public CheckoutResult checkout(
        String shippingName,
        String shippingPhone,
        String shippingAddress,
        String note) {

        // 1. Validate shipping information
        shippingName = validate(
            shippingName, "Tên người nhận", 120
        );

        shippingPhone = validate(
            shippingPhone, "Số điện thoại", 20
        );

        shippingAddress = validate(
            shippingAddress, "Địa chỉ giao hàng", 255
        );

        if (!shippingPhone.matches("^[0-9+() .-]{9,20}$")) {
            throw new IllegalArgumentException(
                "Số điện thoại không hợp lệ."
            );
        }

        if (note != null && (note.length() > 500 || note.indexOf('\0') >= 0)) {
            throw new IllegalArgumentException(
                "Ghi chú không hợp lệ hoặc vượt quá 500 ký tự."
            );
        }

        // 2. Verify authenticated CUSTOMER
        Authentication auth = SecurityContextHolder
            .getContext()
            .getAuthentication();

        if (auth == null
            || !auth.isAuthenticated()
            || auth instanceof AnonymousAuthenticationToken
            || auth.getAuthorities().stream().noneMatch(
            a -> "ROLE_CUSTOMER".equals(a.getAuthority())
        )) {

            throw new SecurityException(
                "Chỉ CUSTOMER được đặt hàng."
            );
        }

        User user = userRepository
            .findWithRolesByUsername(auth.getName())
            .orElseThrow(() ->
                new SecurityException(
                    "Không tìm thấy tài khoản."
                )
            );

        if (user.getStatus() != UserStatus.ACTIVE
            || !user.hasRole("CUSTOMER")) {

            throw new SecurityException(
                "Tài khoản CUSTOMER không hoạt động."
            );
        }

        // 3. Load cart
        Cart cart = cartRepository
            .findWithItemsByUserId(user.getId())
            .orElseThrow(() ->
                new IllegalStateException(
                    "Giỏ hàng đang trống."
                )
            );

        if (cart.isEmpty()) {
            throw new IllegalStateException(
                "Giỏ hàng đang trống."
            );
        }

        // Snapshot product IDs and quantities
        record RequestedItem(
            Long productId,
            int quantity,
            BigDecimal cartPrice
        ) {}

        List<RequestedItem> requested = new ArrayList<>();

        for (CartItem item : cart.getItems()) {

            requested.add(new RequestedItem(
                item.getProduct().getId(),
                item.getQuantity(),
                item.getUnitPrice()
            ));
        }

        // Lock products in deterministic order
        requested.sort(
            Comparator.comparing(RequestedItem::productId)
        );

        // 4. Create order
        Order order = Order.forUser(user);

        long sequence = orderRepository.nextOrderNumber();

        String orderCode = "ORD-"
            + LocalDate.now(BUSINESS_ZONE)
            .format(DateTimeFormatter.BASIC_ISO_DATE)
            + "-"
            + String.format("%06d", sequence);

        order.assignOrderCode(orderCode);

        order.setShippingName(shippingName);
        order.setShippingPhone(shippingPhone);
        order.setShippingAddress(shippingAddress);
        order.setNote(note == null ? null : note.trim());

        // 5. Validate and reserve stock
        for (RequestedItem item : requested) {

            Product product = entityManager.find(
                Product.class,
                item.productId(),
                LockModeType.PESSIMISTIC_WRITE
            );

            if (product == null || !product.isSellable()) {
                throw new IllegalStateException(
                    "Sản phẩm không còn được bán."
                );
            }

            if (item.quantity() <= 0) {
                throw new IllegalStateException(
                    "Số lượng sản phẩm không hợp lệ."
                );
            }

            if (!product.hasSufficientStock(item.quantity())) {
                throw new IllegalStateException(
                    "Không đủ tồn kho cho "
                        + product.getName()
                );
            }

            // Prevent silently charging a changed price
            if (product.getPrice()
                .compareTo(item.cartPrice()) != 0) {

                throw new IllegalStateException(
                    "Giá sản phẩm "
                        + product.getName()
                        + " đã thay đổi. "
                        + "Vui lòng cập nhật giỏ hàng."
                );
            }

            order.addItem(product, item.quantity());
            product.decreaseStock(item.quantity());
        }

        // 6. Persist order
        orderRepository.saveAndFlush(order);

        int totalQuantity = order.totalQuantity();
        BigDecimal totalAmount = order.getTotalAmount();

        // 7. Clear cart
        cart.clear();
        cartRepository.saveAndFlush(cart);

        // 8. Record security event in the same transaction
        securityEventService.recordOrderCreated(
            user,
            user.getUsername(),
            orderCode
        );

        // 9. Return checkout result
        return new CheckoutResult(
            orderCode,
            totalAmount,
            totalQuantity
        );
    }

    // ==========================================
    // VALIDATE INPUT
    // ==========================================

    private String validate(
        String value,
        String field,
        int maxLength) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                field + " không được để trống."
            );
        }

        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " chứa ký tự không hợp lệ.");
        }

        String result = value.trim();

        if (result.length() > maxLength) {
            throw new IllegalArgumentException(
                field + " quá dài."
            );
        }

        return result;
    }
}
