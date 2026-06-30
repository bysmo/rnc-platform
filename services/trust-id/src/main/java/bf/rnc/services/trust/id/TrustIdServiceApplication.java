package bf.rnc.services.trust.id;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RNC — Trust ID
 *
 * <p>Identité financière numérique des citoyens du Burkina Faso.</p>
 *
 * <p>Ce microservice gère :</p>
 * <ul>
 *   <li>L'inscription des citoyens avec OTP SMS</li>
 *   <li>La vérification KYC (CIN Burkina Faso)</li>
 *   <li>Le screening sanctions (LCB-FT — Loi 049-2008/AN)</li>
 *   <li>La gestion du consentement (Loi 010-2004/AN)</li>
 *   <li>L'intégration avec Keycloak pour l'authentification</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.id", "bf.rnc.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class TrustIdServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrustIdServiceApplication.class, args);
    }
}
