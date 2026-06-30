package bf.rnc.infrastructure.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * Service d'authentification RNC.
 * Wraps Keycloak avec des endpoints métier (registrement citoyen, MFA OTP SMS, etc.).
 */
@SpringBootApplication
@EnableEurekaClient
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
