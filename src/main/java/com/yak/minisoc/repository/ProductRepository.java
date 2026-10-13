package com.yak.minisoc.repository;

import com.yak.minisoc.entity.Product;
import com.yak.minisoc.entity.ProductStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for products.
 *
 * <p><strong>Every list method returns {@link Page}, not {@code List}.</strong> The shop
 * will hold more products than a screen can show (NFR-PERF-01), and a method that returns
 * "all products" is an invitation to load the whole catalogue into memory. Paging is
 * therefore not an optimisation added later - it is the shape of the API from the start.</p>
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** The product detail page needs the category name: fetch it in the same query. */
    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findByCategoryIdAndStatus(Long categoryId, ProductStatus status, Pageable pageable);

    /**
     * Case-insensitive search by product name.
     *
     * <p>Derived from the method name, so the JPQL is generated as {@code lower(name) like
     * lower(?)} - which is exactly the expression the functional index
     * {@code idx_products_name_lower} (migration V2) was created for: the index is used
     * for a prefix search, and at least the {@code lower()} conversion is not repeated per
     * row.</p>
     */
    Page<Product> findByNameContainingIgnoreCaseAndStatus(String name, ProductStatus status,
                                                          Pageable pageable);

    /**
     * Inventory check for the admin screen: everything at or below the threshold.
     *
     * <p>A separate query instead of {@code findAll().stream().filter(...)}: the filtering
     * must happen in the database, otherwise the check itself becomes slow exactly when
     * the catalogue is large - the moment it is needed.</p>
     */
    @Query("select p from Product p where p.stock <= :threshold order by p.stock asc, p.name asc")
    List<Product> findLowStock(@Param("threshold") int threshold);

    long countByStatus(ProductStatus status);

    long countByStockLessThanEqual(int threshold);
}
