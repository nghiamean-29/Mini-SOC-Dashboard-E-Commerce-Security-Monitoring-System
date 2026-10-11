package com.yak.minisoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * Product category ({@code categories} table).
 *
 * <p>Deliberately without a {@code @OneToMany List<Product> products} back reference. A
 * category can hold thousands of products, and nobody ever needs "all products of this
 * category" through the Category object - Phase 4 asks the repository for a paged query.
 * Mapping the collection "just in case" would turn every category load into a potential
 * full-catalogue load (and is a classic source of accidental N+1 queries).</p>
 *
 * <p>Categories are deactivated ({@link #deactivate()}) rather than deleted, so products
 * and their history keep a meaningful grouping.</p>
 */
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** URL friendly identifier, e.g. "mechanical-keyboards" (unique, lowercase). */
    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Category() {
    }

    public static Category create(String name, String slug, String description) {
        Category category = new Category();
        category.name = Objects.requireNonNull(name, "name");
        category.slug = Objects.requireNonNull(slug, "slug");
        category.description = description;
        category.active = true;
        return category;
    }

    public void rename(String newName, String newSlug) {
        this.name = Objects.requireNonNull(newName, "newName");
        this.slug = Objects.requireNonNull(newSlug, "newSlug");
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }
}
