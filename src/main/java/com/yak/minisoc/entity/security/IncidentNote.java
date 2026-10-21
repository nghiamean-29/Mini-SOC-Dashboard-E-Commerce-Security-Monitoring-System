
package com.yak.minisoc.entity.security;

import com.yak.minisoc.entity.BaseEntity;
import com.yak.minisoc.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * One entry of the investigation notebook.
 *
 * Append-only by design: a note is evidence of what the analyst knew
 * and concluded at a given moment.
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

    /**
     * Denormalised copy so the note survives account deletion.
     */
    @Column(name = "author_username", length = 50)
    private String authorUsername;

    @Column(name = "content", nullable = false)
    private String content;

    protected IncidentNote() {
    }

    /**
     * Aggregate-internal factory.
     * Use SecurityIncident.addNote() to keep the list in sync.
     */
    static IncidentNote of(
        SecurityIncident incident,
        User author,
        String authorUsername,
        String content,
        Instant createdAt) {

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                "note content must not be blank");
        }

        IncidentNote note = new IncidentNote();

        note.incident = Objects.requireNonNull(
            incident, "incident");

        note.author = author;
        note.authorUsername = authorUsername;
        note.content = content.trim();

        /*
         * BaseEntity uses Hibernate @CreationTimestamp.
         * There is no setCreatedAt() method, so createdAt cannot
         * be assigned directly through the current BaseEntity API.
         */
        Objects.requireNonNull(createdAt, "createdAt");

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
