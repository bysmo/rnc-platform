package bf.rnc.services.trust.credit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RNC — Trust Credit
 *
 * <p>Nano-crédit instantané pour citoyens burkinabè.</p>
 *
 * <p>Ce microservice gère :</p>
 * <ul>
 *   <li>Demande, analyse et approbation des nano-crédits</li>
 *   <li>Génération automatique de l'échéancier</li>
 *   <li>Suivi des paiements (idempotents)</li>
 *   <li>Détection des défauts (>30 jours)</li>
 *   <li>Audit complet du cycle de vie</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.credit", "bf.rnc.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class TrustCreditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrustCreditServiceApplication.class, args);
    }
}
