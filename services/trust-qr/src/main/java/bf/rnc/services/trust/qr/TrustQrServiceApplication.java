package bf.rnc.services.trust.qr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RNC — Trust QR
 *
 * <p>QR Code Confiance — paiement et déclenchement de nano-crédit.</p>
 */
@SpringBootApplication(scanBasePackages = {"bf.rnc.services.trust.qr", "bf.rnc.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class TrustQrServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrustQrServiceApplication.class, args);
    }
}
