package com.yak.minisoc.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yak.minisoc.entity.Cart;
import com.yak.minisoc.entity.Product;
import com.yak.minisoc.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Persistence tests for the cart aggregate: {@link CartRepository} and
 * {@link CartItemRepository}.
 */
@DataJpaTest
// Flyway is NOT part of the @DataJpaTest slice in Spring Boot 4: the slice only
// imports the JPA/JDBC auto-configurations, so the migrations must be imported
// explicitly or the schema would simply not exist when Hibernate validates it.
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CartRepositoryTest {

    @Autowired
    private CartRepository carts;

    @Autowired
    private CartItemRepository cartItems;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    @PersistenceContext
    private EntityManager em;

    private User aCustomer() {
        return users.saveAndFlush(User.create("shopper", "shopper@example.com", "$2a$10$hash", null));
    }

    private Product aProduct(String slug, String price, int stock) {
        return products.saveAndFlush(Product.create("Product " + slug, slug, new BigDecimal(price), stock, null));
    }

    @Test
    @DisplayName("adding the same product twice keeps one line and sums the quantity (upsert)")
    void addingSameProductMergesLines() {
        User user = aCustomer();
        Product product = aProduct("yak-68", "99.00", 10);
        Cart cart = carts.saveAndFlush(Cart.forUser(user));

        cart.addItem(product, 1);
        cart.addItem(product, 2);
        carts.saveAndFlush(cart);
        em.clear();

        Cart reloaded = carts.findWithItemsByUserId(user.getId()).orElseThrow();
        assertThat(reloaded.getItems()).hasSize(1);
        assertThat(reloaded.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(reloaded.totalQuantity()).isEqualTo(3);
        assertThat(reloaded.totalPrice()).isEqualByComparingTo(new BigDecimal("297.00"));
    }

    @Test
    @DisplayName("uk_carts_user: a second cart for the same user is refused by the database")
    void oneCartPerUser() {
        User user = aCustomer();
        carts.saveAndFlush(Cart.forUser(user));

        assertThatThrownBy(() -> carts.saveAndFlush(Cart.forUser(user)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the item keeps the price from the moment it was added (price snapshot)")
    void priceSnapshotSurvivesPriceChange() {
        User user = aCustomer();
        Product product = aProduct("yak-65", "89.00", 10);
        Cart cart = carts.saveAndFlush(Cart.forUser(user));
        cart.addItem(product, 1);
        carts.saveAndFlush(cart);

        // The shop raises the price after the customer filled the cart.
        product.changePrice(new BigDecimal("119.00"));
        products.saveAndFlush(product);
        em.clear();

        Cart reloaded = carts.findWithItemsByUserId(user.getId()).orElseThrow();
        assertThat(reloaded.getItems().get(0).getUnitPrice())
                .as("the customer must still see the price they added")
                .isEqualByComparingTo(new BigDecimal("89.00"));
        assertThat(reloaded.totalPrice()).isEqualByComparingTo(new BigDecimal("89.00"));
    }

    @Test
    @DisplayName("uk_cart_items_cart_product: the same product cannot appear twice in one cart")
    void oneLinePerProductIsEnforcedByTheDatabase() {
        User user = aCustomer();
        Product product = aProduct("yak-50", "49.00", 10);
        Cart cart = carts.saveAndFlush(Cart.forUser(user));
        cart.addItem(product, 1);
        carts.saveAndFlush(cart);
        em.clear();

        Cart managed = carts.findById(cart.getId()).orElseThrow();
        assertThat(cartItems.findByCartIdAndProductId(managed.getId(), product.getId()))
                .isPresent();

        // Bypass the entity (which merges lines) and insert the duplicate row directly
        // with SQL: the "one line per product" rule must hold even if some future code
        // path forgets to go through Cart.addItem().
        assertThatThrownBy(() -> {
            em.createNativeQuery("""
                    insert into cart_items (cart_id, product_id, quantity, unit_price)
                    values (:cartId, :productId, 1, 49.00)
                    """)
                    .setParameter("cartId", managed.getId())
                    .setParameter("productId", product.getId())
                    .executeUpdate();
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ck_cart_items_quantity: more than 999 units of one product is refused")
    void quantityIsBoundedByTheDatabase() {
        User user = aCustomer();
        Product product = aProduct("bulk", "1.00", 5000);
        Cart cart = carts.saveAndFlush(Cart.forUser(user));

        // The entity only refuses non-positive quantities; the upper bound is a rule of
        // the shop and lives in the database (V3).
        cart.addItem(product, 1000);

        assertThatThrownBy(() -> carts.saveAndFlush(cart))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("removing a line deletes the row (orphanRemoval), it does not stay behind")
    void removingItemDeletesTheRow() {
        User user = aCustomer();
        Product product = aProduct("removable", "10.00", 5);
        Cart cart = carts.saveAndFlush(Cart.forUser(user));
        cart.addItem(product, 2);
        carts.saveAndFlush(cart);
        assertThat(cartItems.count()).isEqualTo(1);

        cart.removeItem(product);
        carts.saveAndFlush(cart);

        assertThat(cartItems.count()).isZero();
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("sumQuantityByProductId aggregates in SQL")
    void sumsDemandForAProduct() {
        User first = aCustomer();
        User second = users.saveAndFlush(
                User.create("shopper2", "shopper2@example.com", "$2a$10$hash", null));
        Product product = aProduct("wanted", "10.00", 100);

        Cart cartA = carts.saveAndFlush(Cart.forUser(first));
        cartA.addItem(product, 2);
        carts.saveAndFlush(cartA);

        Cart cartB = carts.saveAndFlush(Cart.forUser(second));
        cartB.addItem(product, 3);
        carts.saveAndFlush(cartB);

        assertThat(cartItems.sumQuantityByProductId(product.getId())).isEqualTo(5);
    }
}
