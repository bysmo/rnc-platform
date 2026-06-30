package bf.rnc.services.trust.qr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustQrService
 * Paiement par QR Code Confiance — génération, scan, autorisation, plafonds
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.qr", "bf.rnc.common"})
@EnableFeignClients
public class TrustQrServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustQrServiceApplication.class, args);
    }
}
