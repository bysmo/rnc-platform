package bf.rnc.services.trust.escrow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustEscrowService
 * Compte d'affectation des financements — réservation, déblocage progressif, validation livraison
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.escrow", "bf.rnc.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class TrustEscrowServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustEscrowServiceApplication.class, args);
    }
}
