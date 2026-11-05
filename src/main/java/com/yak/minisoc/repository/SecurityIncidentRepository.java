
package com.yak.minisoc.repository;

import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.SecurityIncident;
import com.yak.minisoc.entity.security.Severity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for security incidents.
 *
 * Supports incident management, alert correlation,
 * deduplication and SOC dashboard statistics.
 */
public interface SecurityIncidentRepository
    extends JpaRepository<SecurityIncident, Long> {

    // ==========================================
    // INCIDENT DETAILS
    // ==========================================

    Optional<SecurityIncident> findByIncidentCode(
        String incidentCode
    );

    boolean existsByIncidentCode(
        String incidentCode
    );

    /**
     * Loads an incident with its linked alerts
     * and investigation notes.
     */
    @EntityGraph(attributePaths = {"alerts", "notes"})
    Optional<SecurityIncident> findWithDetailsByIncidentCode(
        String incidentCode
    );

    // ==========================================
    // PHASE 7.2 - INCIDENT DEDUPLICATION
    // ==========================================

    /**
     * Finds an incident already linked to a security alert.
     *
     * Used to avoid creating duplicate incidents
     * from the same alert.
     */
    @Query("""
        select i from SecurityIncident i
        join i.alerts a
        where a.id = :alertId
        """)
    Optional<SecurityIncident> findByAlertId(
        @Param("alertId") Long alertId
    );

    // ==========================================
    // INCIDENT CORRELATION
    // ==========================================

    /**
     * Finds active incidents from a source IP,
     * ordered from oldest to newest.
     */
    @Query("""
        select i from SecurityIncident i
        where i.sourceIp = :sourceIp
          and i.status in (
              com.yak.minisoc.entity.security.IncidentStatus.OPEN,
              com.yak.minisoc.entity.security.IncidentStatus.INVESTIGATING
          )
        order by i.detectedAt asc
        """)
    List<SecurityIncident> findOpenWithAlertsBySourceIp(
        @Param("sourceIp") String sourceIp
    );

    // ==========================================
    // INCIDENT LISTS
    // ==========================================

    Page<SecurityIncident> findAllByOrderByDetectedAtDesc(
        Pageable pageable
    );

    Page<SecurityIncident> findByStatusOrderByDetectedAtDesc(
        IncidentStatus status,
        Pageable pageable
    );

    List<SecurityIncident> findByAssignedToIdOrderByDetectedAtDesc(
        Long analystId
    );

    // ==========================================
    // SOC DASHBOARD STATISTICS
    // ==========================================

    long countByStatus(
        IncidentStatus status
    );

    long countBySeverityAndStatus(
        Severity severity,
        IncidentStatus status
    );

    long countByDetectedAtAfter(
        Instant since
    );

    // ==========================================
    // INCIDENT CODE GENERATION
    // ==========================================

    /**
     * Generates the next incident number.
     *
     * Example: INC-000001
     */
    @Query(
        value = "select nextval('incident_code_seq')",
        nativeQuery = true
    )
    long nextIncidentNumber();
}
