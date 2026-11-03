
package com.yak.minisoc.service;

import com.yak.minisoc.entity.*;
import com.yak.minisoc.repository.CartRepository;
import com.yak.minisoc.repository.ProductRepository;
import com.yak.minisoc.repository.UserRepository;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public record CartLine(
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        int stock
    ) {}

    public record CartSummary(
        List<CartLine> items,
        int totalQuantity,
        BigDecimal totalPrice
    ) {}

    public CartService(
        CartRepository cartRepository,
        ProductRepository productRepository,
        UserRepository userRepository) {

        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    private User currentCustomer() {

        Authentication auth =
            SecurityContextHolder.getContext().getAuthentication();

        if (auth == null
            || !auth.isAuthenticated()
            || auth instanceof AnonymousAuthenticationToken
            || auth.getAuthorities().stream().noneMatch(
            a -> "ROLE_CUSTOMER".equals(a.getAuthority())
        )) {
            throw new SecurityException(
                "Bạn cần đăng nhập bằng tài khoản CUSTOMER."
            );
        }

        User user = userRepository
            .findWithRolesByUsername(auth.getName())
            .orElseThrow(() ->
                new SecurityException("Không tìm thấy tài khoản.")
            );

        if (user.getStatus() != UserStatus.ACTIVE
            || !user.hasRole("CUSTOMER")) {
            throw new SecurityException(
                "Tài khoản CUSTOMER không hoạt động."
            );
        }

        return user;
    }

    @Transactional(readOnly = true)
    public CartSummary getMyCart() {

        User user = currentCustomer();

        return cartRepository.findWithItemsByUserId(user.getId())
            .map(this::toSummary)
            .orElseGet(() -> new CartSummary(
                List.of(),
                0,
                BigDecimal.ZERO
            ));
    }

    private CartSummary toSummary(Cart cart) {

        List<CartLine> lines = cart.getItems().stream()
            .map(item -> new CartLine(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.lineTotal(),
                item.getProduct().getStock()
            ))
            .toList();

        return new CartSummary(
            lines,
            cart.totalQuantity(),
            cart.totalPrice()
        );
    }

    @Transactional
    public int addToCart(Long productId, int quantity) {

        if (productId == null || quantity <= 0) {
            throw new IllegalArgumentException(
                "Sản phẩm hoặc số lượng không hợp lệ."
            );
        }

        User user = currentCustomer();

        Product product = productRepository.findById(productId)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Không tìm thấy sản phẩm."
                )
            );

        if (!product.isSellable()) {
            throw new IllegalStateException(
                "Sản phẩm hiện không được bán."
            );
        }

        Cart cart = cartRepository
            .findWithItemsByUserId(user.getId())
            .orElseGet(() -> Cart.forUser(user));

        int existing = cart.findItem(product)
            .map(CartItem::getQuantity)
            .orElse(0);

        long total = (long) existing + quantity;

        if (total > Integer.MAX_VALUE
            || total > product.getStock()) {
            throw new IllegalStateException(
                "Không đủ tồn kho. Hiện có: "
                    + product.getStock()
            );
        }

        cart.addItem(product, quantity);
        cartRepository.save(cart);

        return cart.totalQuantity();
    }

    @Transactional
    public void updateQuantity(Long productId, int quantity) {

        if (productId == null) {
            throw new IllegalArgumentException(
                "Sản phẩm không hợp lệ."
            );
        }

        User user = currentCustomer();

        Cart cart = cartRepository
            .findWithItemsByUserId(user.getId())
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Giỏ hàng không tồn tại."
                )
            );

        Product product = cart.getItems().stream()
            .map(CartItem::getProduct)
            .filter(p -> productId.equals(p.getId()))
            .findFirst()
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Sản phẩm không có trong giỏ."
                )
            );

        if (quantity <= 0) {
            cart.removeItem(product);
        } else {
            if (!product.isSellable()) {
                throw new IllegalStateException(
                    "Sản phẩm hiện không được bán."
                );
            }

            if (quantity > product.getStock()) {
                throw new IllegalStateException(
                    "Không đủ tồn kho. Hiện có: "
                        + product.getStock()
                );
            }

            cart.changeQuantity(product, quantity);
        }

        cartRepository.save(cart);
    }

    @Transactional
    public void removeFromCart(Long productId) {
        updateQuantity(productId, 0);
    }
}
