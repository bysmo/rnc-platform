package bf.rnc.services.trust.farming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustFarmingService
 * Financement agricole — coopératives, intrants, semences, calendrier saisonnier
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.farming", "bf.rnc.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class TrustFarmingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustFarmingServiceApplication.class, args);
    }
}
