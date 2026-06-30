package bf.rnc.common.lib.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Champs d'audit communs à toutes les entités RNC.
 * Conformément à la Loi 010-2004/AN sur la protection des données,
 * toute création/modification doit être traçable.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseAuditFields {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @PrePersist
    void prePersist() {
        if (version == null) version = 0L;
    }

    @PreUpdate
    void preUpdate() {
        version = (version == null ? 0L : version) + 1L;
    }
}
