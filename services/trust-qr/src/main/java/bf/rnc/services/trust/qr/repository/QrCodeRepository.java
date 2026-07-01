package bf.rnc.services.trust.qr.repository;

import bf.rnc.services.trust.qr.entity.QrCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QrCodeRepository extends JpaRepository<QrCode, UUID> {

    Optional<QrCode> findByQrReferenceAndDeletedFalse(String qrReference);

    Page<QrCode> findByMerchantIdAndDeletedFalseOrderByCreatedAtDesc(String merchantId, Pageable pageable);

    Page<QrCode> findByCitizenIdAndDeletedFalseOrderByCreatedAtDesc(String citizenId, Pageable pageable);

    boolean existsByPayloadHashAndDeletedFalse(String payloadHash);
}
