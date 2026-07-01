package bf.rnc.services.trust.qr.repository;

import bf.rnc.services.trust.qr.entity.QrScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QrScanRepository extends JpaRepository<QrScan, UUID> {

    Page<QrScan> findByQrCodeIdOrderByScannedAtDesc(UUID qrCodeId, Pageable pageable);

    Page<QrScan> findByCitizenIdOrderByScannedAtDesc(String citizenId, Pageable pageable);

    long countByQrCodeId(UUID qrCodeId);
}
