package bf.rnc.services.trust.health;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustHealthService
 * Financement santé — centres de santé, pharmacies, urgences médicales
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.health", "bf.rnc.common"})
@EnableFeignClients
public class TrustHealthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustHealthServiceApplication.class, args);
    }
}
