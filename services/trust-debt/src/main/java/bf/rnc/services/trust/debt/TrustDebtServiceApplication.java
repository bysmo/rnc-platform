package bf.rnc.services.trust.debt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustDebtService
 * Reconnaissance de dette entre particuliers — consentement, horodatage, rappels
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.debt", "bf.rnc.common"})
@EnableFeignClients
public class TrustDebtServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustDebtServiceApplication.class, args);
    }
}
