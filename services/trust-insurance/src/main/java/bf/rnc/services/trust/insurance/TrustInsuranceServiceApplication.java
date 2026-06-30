package bf.rnc.services.trust.insurance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustInsuranceService
 * Micro-assurance — couverture crédit, santé, récolte, primes indexées
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.insurance", "bf.rnc.common"})
@EnableEurekaClient
@EnableFeignClients
public class TrustInsuranceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustInsuranceServiceApplication.class, args);
    }
}
