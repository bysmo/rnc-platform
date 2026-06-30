package bf.rnc.services.trust.id;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustIdService
 * Identité financière numérique — KYC, gestion Citoyens, CIN Burkina Faso, validation biométrique
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.id", "bf.rnc.common"})
@EnableEurekaClient
@EnableFeignClients
public class TrustIdServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustIdServiceApplication.class, args);
    }
}
