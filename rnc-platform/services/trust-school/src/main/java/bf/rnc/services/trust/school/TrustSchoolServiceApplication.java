package bf.rnc.services.trust.school;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustSchoolService
 * Financement scolaire — écoles partenaires, frais de scolarité, déblocage direct
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.school", "bf.rnc.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class TrustSchoolServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustSchoolServiceApplication.class, args);
    }
}
