package bf.rnc.services.trust.score;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RNC — Trust Score
 *
 * <p>Réputation financière nationale des citoyens burkinabè.</p>
 *
 * <p>Ce microservice gère :</p>
 * <ul>
 *   <li>Le calcul et la mise à jour du Trust Score (0-1000)</li>
 *   <li>L'explicabilité : chaque événement tracé</li>
 *   <li>Les contestations citoyen (appeals)</li>
 *   <li>L'historique immuable pour audit réglementaire</li>
 *   <li>Les analytics agrégés pour dashboard admin</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.score", "bf.rnc.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class TrustScoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrustScoreServiceApplication.class, args);
    }
}
