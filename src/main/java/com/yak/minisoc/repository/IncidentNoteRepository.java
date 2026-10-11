package com.yak.minisoc.repository;

import com.yak.minisoc.entity.security.IncidentNote;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for incident notes.
 *
 * <p>Notes are normally added through the incident ({@code incident.addNote(...)}^{\*}), so
 * this repository is mostly a read interface - with one deliberate exception:
 * {@link #findAllByOrderByCreatedAtDesc(Pageable)} for the cross-incident activity feed
 * ("what has the team been doing today?"). A note is never deleted on its own either; it
 * disappears only with its incident ({@code ON DELETE CASCADE}).</p>
 *
 * <p>^{\*} That is why there is no {@code saveNote(incident, text)} helper here: the note
 * must be added to the parent entity, otherwise the in-memory list of the incident would
 * not contain it after the insert.</p>
 */
public interface IncidentNoteRepository extends JpaRepository<IncidentNote, Long> {

    List<IncidentNote> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);

    Page<IncidentNote> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByIncidentId(Long incidentId);

    long countByCreatedAtAfter(java.time.Instant since);
}
