package bf.rnc.services.trust.score.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rncTrustScoreOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RNC — Trust Score API")
                        .description("Réputation financière nationale des citoyens du Burkina Faso")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("RNC Architecture Team")
                                .email("dev@rnc.bf")
                                .url("https://rnc.bf"))
                        .license(new License()
                                .name("Propriétaire — République du Burkina Faso")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT OIDC fourni par Keycloak")));
    }
}
