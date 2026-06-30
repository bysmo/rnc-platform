package bf.rnc.services.trust.score;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * RNC — TrustScoreService
 * Réputation financière nationale — Trust Score dynamique, algorithme explicable, audit régulier
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.score", "bf.rnc.common"})
@EnableFeignClients
public class TrustScoreServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustScoreServiceApplication.class, args);
    }
}
