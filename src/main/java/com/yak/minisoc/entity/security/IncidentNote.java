package com.yak.minisoc.entity.security;

import com.yak.minisoc.entity.BaseEntity;
import com.yak.minisoc.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * One entry of the investigation notebook ({@code incident_notes} table, FR-INC-05).
 *
 * <p>Append-only by design: a note is evidence of what the analyst knew and concluded at a
 * given moment. Editing it afterwards would rewrite history - the very thing an
 * investigation record exists to prevent. There is therefore no setter for {@code content},
 * and the database constraint {@code ck_incident_notes_content} refuses whitespace-only
 * text.</p>
 *
 * <p>{@link #authorUsername} is stored next to the {@code author} reference, for the same
 * reason as in {@code audit_logs}: the notebook must stay readable after the account is
 * deleted (the foreign key becomes NULL, the name survives).</p>
 *
 * <p>Extends {@link BaseEntity} - {@code created_at}/{@code updated_at} rather than
 * {@code occurred_at}: the question a reader asks about a note is "when was it written",
 * which is exactly what {@code created_at} answers.</p>
 */
@Entity
@Table(name = "incident_notes")
public class IncidentNote extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private SecurityIncident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    /** Denormalised copy so the note survives the deletion of the account. */
    @Column(name = "author_username", length = 50)
    private String authorUsername;

    @Column(name = "content", nullable = false)
    private String content;

    protected IncidentNote() {
    }

    /** Aggregate-internal factory; use {@link SecurityIncident#addNote} so the list stays in sync. */
    static IncidentNote of(SecurityIncident incident, User author, String authorUsername,
                           String content, Instant createdAt) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("note content must not be blank");
        }
        IncidentNote note = new IncidentNote();
        note.incident = Objects.requireNonNull(incident, "incident");
        note.author = author;
        note.authorUsername = authorUsername;
        note.content = content.trim();
        // Keep the moment the note was written explicit: the API layer passes the request
        // timestamp, and a fixed value can be injected in tests.
        note.setCreatedAt(Objects.requireNonNull(createdAt, "createdAt"));
        return note;
    }

    public SecurityIncident getIncident() {
        return incident;
    }

    public User getAuthor() {
        return author;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public String getContent() {
        return content;
    }
}
