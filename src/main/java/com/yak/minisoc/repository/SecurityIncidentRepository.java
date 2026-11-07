
package com.yak.minisoc.repository;

import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.SecurityIncident;
import com.yak.minisoc.entity.security.Severity;

import com.yak.minisoc.entity.security.IncidentType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // Serialize candidate lookup and creation even when no incident row exists.
    @Query(value = """
        select 1 from pg_advisory_xact_lock(
            hashtextextended(concat('minisoc:incident:', :sourceIp, ':', :family), 0)
        )
        """, nativeQuery = true)
    int lockCorrelationKey(@Param("sourceIp") String sourceIp, @Param("family") String family);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select i from SecurityIncident i
        where i.sourceIp = :sourceIp
          and i.incidentType = :incidentType
          and i.status in (
              com.yak.minisoc.entity.security.IncidentStatus.OPEN,
              com.yak.minisoc.entity.security.IncidentStatus.INVESTIGATING
          )
          and i.detectedAt between :earliest and :latest
          and not exists (
              select a.id from SecurityIncident candidate join candidate.alerts a
              where candidate.id = i.id
                and (a.detectedAt < :earliest or a.detectedAt > :latest)
          )
        order by i.detectedAt desc, i.id desc
        """)
    List<SecurityIncident> findCorrelationCandidates(
        @Param("sourceIp") String sourceIp,
        @Param("incidentType") IncidentType incidentType,
        @Param("earliest") Instant earliest,
        @Param("latest") Instant latest,
        Pageable pageable
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
