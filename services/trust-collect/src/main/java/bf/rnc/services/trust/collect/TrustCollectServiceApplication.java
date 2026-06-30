package bf.rnc.services.trust.collect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustCollectService
 * Recouvrement amiable automatisé — rappels, escalade, négociation
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.collect", "bf.rnc.common"})
@EnableEurekaClient
@EnableFeignClients
public class TrustCollectServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustCollectServiceApplication.class, args);
    }
}
