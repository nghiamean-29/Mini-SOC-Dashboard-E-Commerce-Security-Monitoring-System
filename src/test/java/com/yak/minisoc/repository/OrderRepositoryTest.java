package com.yak.minisoc.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yak.minisoc.entity.Order;
import com.yak.minisoc.entity.OrderStatus;
import com.yak.minisoc.entity.Product;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.util.BusinessCodes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Persistence tests for orders: {@link OrderRepository} and {@link OrderItemRepository}.
 *
 * <p>Three of the most important design rules of the project are verified here:
 * price snapshots, the order state machine and the sequence-based order code.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderItemRepository orderItems;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    @PersistenceContext
    private EntityManager em;

    private User aCustomer() {
        return users.saveAndFlush(User.create("buyer", "buyer@example.com", "$2a$10$hash", null));
    }

    private Product aProduct(String slug, String price) {
        return products.saveAndFlush(
                Product.create("Product " + slug, slug, new BigDecimal(price), 100, null));
    }

    private Order anOrder(User user, Product keyboard, int keyboards, Product mouse, int mice) {
        Order order = Order.forUser(user);
        order.assignOrderCode(BusinessCodes.orderCode(Instant.now(), orders.nextOrderNumber()));
        order.addItem(keyboard, keyboards);
        order.addItem(mouse, mice);
        return orders.saveAndFlush(order);
    }

    @Test
    @DisplayName("the total is the sum of the line totals (BigDecimal, not double)")
    void totalIsTheSumOfLines() {
        User user = aCustomer();
        Product keyboard = aProduct("yak-68", "99.99");
        Product mouse = aProduct("yak-mouse", "0.10");

        Order order = anOrder(user, keyboard, 2, mouse, 3);
        em.clear();

        Order reloaded = orders.findWithItemsByOrderCode(order.getOrderCode()).orElseThrow();
        assertThat(reloaded.getItems()).hasSize(2);
        assertThat(reloaded.totalQuantity()).isEqualTo(5);
        // 2 * 99.99 + 3 * 0.10 = 200.28 exactly; with double this drifts.
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo(new BigDecimal("200.28"));
    }

    @Test
    @DisplayName("order lines are snapshots: a later price change cannot rewrite the invoice")
    void linesAreSnapshots() {
        User user = aCustomer();
        Product keyboard = aProduct("yak-68", "99.99");
        Order order = anOrder(user, keyboard, 1, aProduct("yak-mouse", "10.00"), 1);

        keyboard.changePrice(new BigDecimal("49.99"));
        keyboard.rename("Yak 68 (new name)", "yak-68");
        products.saveAndFlush(keyboard);
        em.clear();

        Order reloaded = orders.findWithItemsByOrderCode(order.getOrderCode()).orElseThrow();
        assertThat(reloaded.getItems())
                .anySatisfy(item -> {
                    assertThat(item.getProductName()).isEqualTo("Product yak-68");
                    assertThat(item.getUnitPrice()).isEqualByComparingTo(new BigDecimal("99.99"));
                });
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo(new BigDecimal("109.99"));
    }

    @Test
    @DisplayName("nextval(): every call returns a strictly larger number (no collisions)")
    void orderNumbersComeFromASequence() {
        long first = orders.nextOrderNumber();
        long second = orders.nextOrderNumber();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("the generated code fits the column and is unique")
    void orderCodeFitsAndIsUnique() {
        User user = aCustomer();
        Product keyboard = aProduct("yak-68", "99.99");
        Order order = anOrder(user, keyboard, 1, aProduct("yak-mouse", "10.00"), 1);

        assertThat(order.getOrderCode()).matches("ORD-\\d{8}-\\d{6}");
        assertThat(order.getOrderCode().length()).isLessThanOrEqualTo(BusinessCodes.ORDER_CODE_MAX_LENGTH);

        // uk_orders_code: a hand-written code that is already used must be refused.
        Order duplicate = Order.forUser(user);
        duplicate.assignOrderCode(order.getOrderCode());
        duplicate.addItem(keyboard, 1);

        assertThatThrownBy(() -> orders.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the state machine refuses an illegal jump")
    void illegalTransitionIsRefused() {
        Order order = Order.forUser(aCustomer());
        order.addItem(aProduct("yak-68", "99.99"), 1);

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.SHIPPED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("illegal order transition PENDING");
    }

    @Test
    @DisplayName("cancelling records the moment and the reason (ck_orders_cancelled)")
    void cancellingRecordsTheFact() {
        User user = aCustomer();
        Order order = anOrder(user, aProduct("yak-68", "99.99"), 1, aProduct("yak-mouse", "10.00"), 1);

        Instant cancelledAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        order.cancel(cancelledAt, "customer changed their mind");
        orders.saveAndFlush(order);
        em.clear();

        Order reloaded = orders.findByOrderCode(order.getOrderCode()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(reloaded.getCancelledAt()).isEqualTo(cancelledAt);
        assertThat(reloaded.getCancelReason()).isEqualTo("customer changed their mind");
        assertThat(reloaded.isCancellable()).isFalse();
        assertThat(reloaded.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("a shipped order can no longer be cancelled")
    void shippedOrderIsNotCancellable() {
        Order order = Order.forUser(aCustomer());
        order.addItem(aProduct("yak-68", "99.99"), 1);
        order.changeStatus(OrderStatus.CONFIRMED);
        order.changeStatus(OrderStatus.PROCESSING);
        order.changeStatus(OrderStatus.SHIPPED);

        assertThatThrownBy(() -> order.cancel(Instant.now(), "too late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can no longer be cancelled");
    }

    @Test
    @DisplayName("revenue ignores cancelled orders; a period without orders sums to NULL")
    void revenueIgnoresCancelledOrders() {
        User user = aCustomer();
        Product keyboard = aProduct("yak-68", "99.99");
        Product mouse = aProduct("yak-mouse", "10.00");

        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        anOrder(user, keyboard, 1, mouse, 1);                 // 109.99
        Order cancelled = anOrder(user, keyboard, 5, mouse, 5); // will be cancelled
        cancelled.cancel(Instant.now(), "test");
        orders.saveAndFlush(cancelled);

        assertThat(orders.sumRevenueBetween(from, to))
                .isEqualByComparingTo(new BigDecimal("109.99"));

        // SQL rule worth remembering: SUM over zero rows is NULL, not 0.
        assertThat(orders.sumRevenueBetween(Instant.now().plus(2, ChronoUnit.HOURS),
                Instant.now().plus(3, ChronoUnit.HOURS)))
                .isNull();
    }

    @Test
    @DisplayName("the admin queue and the customer history are paged queries")
    void queueAndHistoryQueries() {
        User user = aCustomer();
        Order first = anOrder(user, aProduct("yak-68", "99.99"), 1, aProduct("yak-mouse", "10.00"), 1);
        Order second = anOrder(user, aProduct("yak-65", "89.00"), 1, aProduct("yak-mouse2", "10.00"), 1);
        second.changeStatus(OrderStatus.CONFIRMED);
        orders.saveAndFlush(second);
        em.clear();

        assertThat(orders.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 10)))
                .hasSize(2);

        assertThat(orders.findByStatusOrderByCreatedAtAsc(OrderStatus.PENDING, PageRequest.of(0, 10)))
                .extracting(Order::getOrderCode)
                .containsExactly(first.getOrderCode());

        assertThat(orders.countByStatus(OrderStatus.CONFIRMED)).isEqualTo(1);
        assertThat(orders.existsByOrderCode(second.getOrderCode())).isTrue();
    }

    @Test
    @DisplayName("best sellers are reported from the snapshot columns")
    void topSellingProductsUseSnapshots() {
        User user = aCustomer();
        Product keyboard = aProduct("yak-68", "99.99");
        Product mouse = aProduct("yak-mouse", "10.00");
        anOrder(user, keyboard, 3, mouse, 1);

        var top = orderItems.topSellingProducts(
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.HOURS),
                PageRequest.of(0, 10));

        assertThat(top).hasSize(2);
        assertThat(top.get(0).getProductName()).isEqualTo("Product yak-68");
        assertThat(top.get(0).getUnitsSold()).isEqualTo(3);
        assertThat(top.get(0).getRevenue()).isEqualByComparingTo(new BigDecimal("299.97"));
    }
}
