package com.yak.minisoc.repository;

import com.yak.minisoc.entity.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for roles.
 *
 * <p>Small interface on purpose: the three roles (CUSTOMER, ADMIN, SOC_ANALYST) are seeded
 * by migration V9 and are normally read, never created at runtime. Only the administration
 * of role assignments (Phase 3) needs to look them up.</p>
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);

    List<Role> findAllByOrderByCodeAsc();
}
