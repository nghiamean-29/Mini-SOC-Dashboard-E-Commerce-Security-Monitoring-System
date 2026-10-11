package com.yak.minisoc.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yak.minisoc.entity.Category;
import com.yak.minisoc.entity.Product;
import com.yak.minisoc.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** Persistence tests for the catalogue: {@link CategoryRepository} and {@link ProductRepository}. */
@DataJpaTest
// Flyway is NOT part of the @DataJpaTest slice in Spring Boot 4: the slice only
// imports the JPA/JDBC auto-configurations, so the migrations must be imported
// explicitly or the schema would simply not exist when Hibernate validates it.
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CatalogRepositoryTest {

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProductRepository products;

    @PersistenceContext
    private EntityManager em;

    private Category keyboardCategory() {
        return categories.saveAndFlush(
                Category.create("Keyboards", "keyboards", "Mechanical keyboards"));
    }

    @Test
    @DisplayName("money keeps its scale: 19.99 stored in NUMERIC(12,2) comes back as 19.99")
    void priceKeepsScale() {
        Category category = keyboardCategory();
        Product product = products.saveAndFlush(Product.create("Yak 68", "yak-68",
                new BigDecimal("19.99"), 10, category));

        em.clear();

        Product reloaded = products.findBySlug("yak-68").orElseThrow();
        assertThat(reloaded.getPrice()).isEqualByComparingTo(new BigDecimal("19.99"));
        assertThat(reloaded.getPrice().scale())
                .as("NUMERIC(12,2) must not silently drop the cents")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("the product detail query fetches the category in the same call")
    void fetchesCategoryWithProduct() {
        Category category = keyboardCategory();
        Product product = products.saveAndFlush(
                Product.create("Yak 65", "yak-65", new BigDecimal("89.00"), 5, category));
        em.clear();

        Product loaded = products.findWithCategoryById(product.getId()).orElseThrow();

        assertThat(loaded.getCategory().getName()).isEqualTo("Keyboards");
        assertThat(loaded.getCategory().getSlug()).isEqualTo("keyboards");
    }

    @Test
    @DisplayName("search is case-insensitive and paged")
    void searchesCaseInsensitively() {
        Category category = keyboardCategory();
        products.saveAndFlush(Product.create("Yak 68 Pro", "yak-68-pro",
                new BigDecimal("129.00"), 3, category));
        products.saveAndFlush(Product.create("Mouse Yak", "mouse-yak",
                new BigDecimal("29.00"), 8, category));

        assertThat(products.findByNameContainingIgnoreCaseAndStatus("yak 68", ProductStatus.ACTIVE,
                        PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(p -> assertThat(p.getSlug()).isEqualTo("yak-68-pro"));

        assertThat(products.findByNameContainingIgnoreCaseAndStatus("YAK", ProductStatus.ACTIVE,
                        PageRequest.of(0, 10)))
                .hasSize(2);
    }

    @Test
    @DisplayName("uk_products_slug: two products cannot share a slug")
    void rejectsDuplicateSlug() {
        Category category = keyboardCategory();
        products.saveAndFlush(Product.create("Yak 68", "yak-68", new BigDecimal("99.00"), 1, category));

        assertThatThrownBy(() -> products.saveAndFlush(
                Product.create("Another keyboard", "yak-68", new BigDecimal("50.00"), 1, category)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ck_products_slug_format: the database only accepts url-safe slugs")
    void rejectsUglySlug() {
        Category category = keyboardCategory();

        assertThatThrownBy(() -> products.saveAndFlush(
                Product.create("Bad Slug Product", "Bad Slug!", new BigDecimal("10.00"), 1, category)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the low-stock query filters in SQL, not in Java")
    void findsLowStockProducts() {
        Category category = keyboardCategory();
        products.saveAndFlush(Product.create("Plenty", "plenty", new BigDecimal("10.00"), 50, category));
        products.saveAndFlush(Product.create("Almost gone", "almost-gone", new BigDecimal("10.00"), 3, category));
        products.saveAndFlush(Product.create("Last one", "last-one", new BigDecimal("10.00"), 1, category));

        List<Product> low = products.findLowStock(5);

        assertThat(low).extracting(Product::getSlug)
                .containsExactly("last-one", "almost-gone");   // ordered by stock ascending
        assertThat(products.countByStockLessThanEqual(5)).isEqualTo(2);
    }

    @Test
    @DisplayName("stock operations keep the entity invariant")
    void stockOperationsAreSafe() {
        Product product = Product.create("Stock demo", "stock-demo", new BigDecimal("10.00"), 2, null);

        assertThat(product.hasSufficientStock(2)).isTrue();
        assertThat(product.hasSufficientStock(3)).isFalse();

        product.decreaseStock(2);
        assertThat(product.getStock()).isZero();
        assertThat(product.hasSufficientStock(1)).isFalse();

        assertThatThrownBy(() -> product.decreaseStock(1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> product.setStock(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("@Version: every update increments the optimistic lock counter")
    void versionIncrementsOnUpdate() {
        Product product = products.saveAndFlush(Product.create("Versioned", "versioned",
                new BigDecimal("10.00"), 10, null));
        long initialVersion = product.getVersion();

        product.decreaseStock(1);
        Product updated = products.saveAndFlush(product);

        assertThat(initialVersion).isZero();
        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("an inactive product is not sellable even with stock available")
    void inactiveProductIsNotSellable() {
        Product product = Product.create("Hidden", "hidden", new BigDecimal("10.00"), 10, null);
        product.deactivate();

        assertThat(product.isSellable()).isFalse();
        assertThat(product.hasSufficientStock(1)).isFalse();
    }
}
