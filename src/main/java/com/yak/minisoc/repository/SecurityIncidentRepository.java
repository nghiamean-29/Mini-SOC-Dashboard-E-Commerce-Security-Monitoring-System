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
 * Data access for incidents.
 *
 * <p>Same shape as {@link SecurityAlertRepository}, with one addition: the correlation
 * query {@link #findOpenWithAlertsBySourceIp(String)} - "is there already an open incident
 * from this source that new alerts should join?" - which is how Phase 8 avoids opening a
 * second investigation for an attack that is still going on.</p>
 */
public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident, Long> {

    Optional<SecurityIncident> findByIncidentCode(String incidentCode);

    boolean existsByIncidentCode(String incidentCode);

    /** Incident detail page: incident, its alerts and the investigation notebook. */
    @EntityGraph(attributePaths = {"alerts", "notes"})
    Optional<SecurityIncident> findWithDetailsByIncidentCode(String incidentCode);

    /**
     * Open incidents from one source IP, oldest first.
     *
     * <p>{@code status in (OPEN, INVESTIGATING)} is written in the query rather than passed
     * by the caller: "an incident that is still being worked on" is a property of the
     * workflow, and letting a caller pass an arbitrary list would one day let it pass
     * {@code RESOLVED} and reopen a closed investigation.</p>
     */
    @Query("""
            select i from SecurityIncident i
            where i.sourceIp = :sourceIp
              and i.status in (com.yak.minisoc.entity.security.IncidentStatus.OPEN,
                               com.yak.minisoc.entity.security.IncidentStatus.INVESTIGATING)
            order by i.detectedAt asc
            """)
    List<SecurityIncident> findOpenWithAlertsBySourceIp(@Param("sourceIp") String sourceIp);

    Page<SecurityIncident> findAllByOrderByDetectedAtDesc(Pageable pageable);

    Page<SecurityIncident> findByStatusOrderByDetectedAtDesc(IncidentStatus status, Pageable pageable);

    List<SecurityIncident> findByAssignedToIdOrderByDetectedAtDesc(Long analystId);

    long countByStatus(IncidentStatus status);

    long countBySeverityAndStatus(Severity severity, IncidentStatus status);

    long countByDetectedAtAfter(Instant since);

    /**
     * Next number for the human readable incident code ({@code INC-0001}).
     * See {@code order_code_seq} in migration V4 for the reasoning behind sequences.
     */
    @Query(value = "select nextval('incident_code_seq')", nativeQuery = true)
    long nextIncidentNumber();
}
