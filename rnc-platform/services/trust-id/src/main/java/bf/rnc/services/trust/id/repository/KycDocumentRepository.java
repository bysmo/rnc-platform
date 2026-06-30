package bf.rnc.services.trust.id.repository;

import bf.rnc.services.trust.id.entity.DocumentVerificationStatus;
import bf.rnc.services.trust.id.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findByCitizenIdAndDeletedFalse(UUID citizenId);

    List<KycDocument> findByCitizenIdAndVerificationStatusAndDeletedFalse(
            UUID citizenId, DocumentVerificationStatus status);

    @Query("SELECT d FROM KycDocument d WHERE d.deleted = false " +
           "AND d.verificationStatus = :status " +
           "ORDER BY d.submittedAt ASC")
    List<KycDocument> findPendingDocuments(@Param("status") DocumentVerificationStatus status);

    boolean existsByFileSha256AndDeletedFalse(String fileSha256);
}
