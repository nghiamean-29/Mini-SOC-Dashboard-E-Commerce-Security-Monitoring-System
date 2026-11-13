
package com.yak.minisoc.service;

import com.yak.minisoc.entity.Order;
import com.yak.minisoc.entity.Product;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;

import com.yak.minisoc.repository.OrderRepository;
import com.yak.minisoc.repository.ProductRepository;
import com.yak.minisoc.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class OrderService {

    private static final int MAX_CANCEL_REASON_LENGTH = 255;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SecurityEventService securityEventService;

    // ==========================================
    // DTOs
    // ==========================================

    public record OrderSummary(
        String orderCode,
        String status,
        BigDecimal totalAmount,
        Instant createdAt
    ) {}

    public record OrderLine(
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
    ) {}

    public record OrderDetail(
        String orderCode,
        String status,
        BigDecimal totalAmount,
        Instant createdAt,
        String shippingName,
        String shippingPhone,
        String shippingAddress,
        String note,
        List<OrderLine> items
    ) {}

    // ==========================================
    // CONSTRUCTOR
    // ==========================================

    public OrderService(
        OrderRepository orderRepository,
        UserRepository userRepository,
        ProductRepository productRepository,
        SecurityEventService securityEventService) {

        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.securityEventService = securityEventService;
    }

    // ==========================================
    // GET CURRENT CUSTOMER
    // ==========================================

    private User currentCustomer() {

        Authentication auth = SecurityContextHolder
            .getContext()
            .getAuthentication();

        if (auth == null
            || !auth.isAuthenticated()
            || auth instanceof AnonymousAuthenticationToken
            || auth.getAuthorities().stream().noneMatch(
            authority -> "ROLE_CUSTOMER".equals(
                authority.getAuthority()
            )
        )) {

            throw new SecurityException(
                "Bạn cần đăng nhập bằng tài khoản CUSTOMER."
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
                "Tài khoản không có quyền CUSTOMER."
            );
        }

        return user;
    }

    // ==========================================
    // GET MY ORDERS
    // ==========================================

    @Transactional(readOnly = true)
    public Page<OrderSummary> getMyOrders(
        int page,
        int pageSize) {

        User user = currentCustomer();

        if (page < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException(
                "Thông số phân trang không hợp lệ."
            );
        }

        return orderRepository
            .findByUserIdOrderByCreatedAtDesc(
                user.getId(),
                PageRequest.of(page, pageSize)
            )
            .map(order -> new OrderSummary(
                order.getOrderCode(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt()
            ));
    }

    // ==========================================
    // GET MY ORDER DETAIL
    // ==========================================

    @Transactional(readOnly = true)
    public OrderDetail getMyOrderDetail(String orderCode) {

        User user = currentCustomer();

        validateOrderCode(orderCode);

        Order order = orderRepository
            .findWithItemsByOrderCode(orderCode)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Không tìm thấy đơn hàng."
                )
            );

        validateOrderOwnership(order, user);

        List<OrderLine> lines = order.getItems()
            .stream()
            .map(item -> new OrderLine(
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal()
            ))
            .toList();

        return new OrderDetail(
            order.getOrderCode(),
            order.getStatus().name(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            order.getShippingName(),
            order.getShippingPhone(),
            order.getShippingAddress(),
            order.getNote(),
            lines
        );
    }

    // ==========================================
    // CANCEL MY ORDER + SECURITY EVENT
    // ==========================================

    @Transactional
    public void cancelMyOrder(
        String orderCode,
        String reason) {

        User user = currentCustomer();

        validateOrderCode(orderCode);

        // 1. Validate cancellation reason.
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                "Vui lòng nhập lý do hủy đơn hàng."
            );
        }

        if (reason.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Lý do hủy chứa ký tự không hợp lệ.");
        }
        String cancelReason = reason.trim();

        if (cancelReason.length() > MAX_CANCEL_REASON_LENGTH) {
            throw new IllegalArgumentException(
                "Lý do hủy không được vượt quá 255 ký tự."
            );
        }

        // 2. Load order and its items.
        Order order = orderRepository
            .findWithItemsByOrderCode(orderCode)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Không tìm thấy đơn hàng."
                )
            );

        // 3. Verify order ownership.
        validateOrderOwnership(order, user);

        // 4. Only PENDING / CONFIRMED can be cancelled.
        if (!order.isCancellable()) {
            throw new IllegalStateException(
                "Đơn hàng đang ở trạng thái "
                    + order.getStatus()
                    + " nên không thể hủy."
            );
        }

        if (order.getItems().isEmpty()) {
            throw new IllegalStateException(
                "Đơn hàng không có sản phẩm để hoàn kho."
            );
        }

        // 5. Collect product IDs in a stable order.
        // This helps reduce lock-order conflicts when
        // several transactions update inventory.
        List<Long> productIds = order.getItems()
            .stream()
            .map(item -> {
                if (item.getProduct() == null) {
                    throw new IllegalStateException(
                        "Không thể hoàn kho cho sản phẩm: "
                            + item.getProductName()
                    );
                }

                if (item.getQuantity() <= 0) {
                    throw new IllegalStateException(
                        "Số lượng sản phẩm trong đơn không hợp lệ."
                    );
                }

                return item.getProduct().getId();
            })
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList();

        // 6. Load managed products.
        // Product extends VersionedEntity, so concurrent
        // updates can be detected by optimistic locking.
        java.util.Map<Long, Product> products =
            new java.util.HashMap<>();

        for (Long productId : productIds) {
            Product product = productRepository
                .findById(productId)
                .orElseThrow(() ->
                    new IllegalStateException(
                        "Không tìm thấy sản phẩm ID: "
                            + productId
                    )
                );

            products.put(productId, product);
        }

        // 7. Cancel the order.
        order.cancel(Instant.now(), cancelReason);

        // 8. Restore inventory.
        for (var item : order.getItems()) {

            Long productId = item.getProduct().getId();

            Product product = products.get(productId);

            product.increaseStock(item.getQuantity());
        }

        // 9. Flush database changes.
        // If optimistic locking fails, transaction rolls back.
        orderRepository.saveAndFlush(order);
        productRepository.flush();

        // 10. Record a SOC security event.
        // Uses the same transaction as the cancellation.
        securityEventService.recordOrderCancelled(
            user,
            user.getUsername(),
            order.getOrderCode()
        );
    }

    // ==========================================
    // VALIDATE ORDER CODE
    // ==========================================

    private void validateOrderCode(String orderCode) {

        if (orderCode == null || orderCode.isBlank()) {
            throw new IllegalArgumentException(
                "Mã đơn hàng không hợp lệ."
            );
        }
    }

    // ==========================================
    // VALIDATE ORDER OWNERSHIP
    // ==========================================

    private void validateOrderOwnership(
        Order order,
        User user) {

        if (!order.getUser().getId().equals(user.getId())) {
            throw new SecurityException(
                "Bạn không có quyền truy cập đơn hàng này."
            );
        }
    }
}
