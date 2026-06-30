package bf.rnc.services.trust.credit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustCreditService
 * Nano-crédit instantané — demande, analyse risque, déblocage, remboursement
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.credit", "bf.rnc.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class TrustCreditServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustCreditServiceApplication.class, args);
    }
}
