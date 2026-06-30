package bf.rnc.services.trust.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustMerchantService
 * Gestion des fournisseurs partenaires — onboarding, agrément, QR codes
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.merchant", "bf.rnc.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class TrustMerchantServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustMerchantServiceApplication.class, args);
    }
}
