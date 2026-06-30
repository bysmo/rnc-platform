package bf.rnc.services.trust.id.client;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Client Keycloak Admin — création d'utilisateurs, gestion credentials, assignation rôles.
 *
 * <p>Au MVP, cette classe utilise Keycloak Admin Client pour créer des utilisateurs.
 * En cas d'indisponibilité (env de test sans Keycloak), un mode dégradé journalise seulement.</p>
 */
@Slf4j
@Component
public class KeycloakAdminClient {

    @Value("${keycloak.server-url:http://localhost:8090}")
    private String serverUrl;

    @Value("${keycloak.realm:rnc}")
    private String realm;

    @Value("${keycloak.admin-client-id:admin-cli}")
    private String adminClientId;

    @Value("${keycloak.admin-client-secret:}")
    private String adminClientSecret;

    @Value("${rnc.keycloak.mock:false}")
    private boolean mockMode;

    @PostConstruct
    public void init() {
        log.info("KeycloakAdminClient initialized — server={}, realm={}, mock={}",
                serverUrl, realm, mockMode);
    }

    /**
     * Crée un utilisateur Keycloak pour le citoyen.
     *
     * @param phoneNumber numéro de téléphone (username)
     * @param firstName   prénom
     * @param lastName    nom
     * @param email       email (optionnel)
     * @return keycloak user id (ou identifiant mock si mode mock)
     */
    public String createCitizenUser(String phoneNumber, String firstName, String lastName, String email) {
        if (mockMode) {
            log.warn("[MOCK Keycloak] createCitizenUser({},{},{},{}) — retourne ID mock",
                    phoneNumber, firstName, lastName, email);
            return "mock-keycloak-" + phoneNumber.hashCode();
        }

        // TODO: implémentation réelle avec keycloak-admin-client
        // Keycloak kc = KeycloakBuilder.builder().serverUrl(serverUrl).realm(realm)
        //     .clientId(adminClientId).clientSecret(adminClientSecret).build();
        // UserRepresentation user = new UserRepresentation();
        // user.setUsername(phoneNumber);
        // user.setEmail(email);
        // user.setFirstName(firstName);
        // user.setLastName(lastName);
        // user.setEnabled(false); // activé après OTP
        // Response response = kc.realm(realm).users().create(user);
        // String location = response.getHeaderString("Location");
        // String userId = location.replaceAll(".*/(.*)$", "$1");
        // return userId;

        log.warn("KeycloakAdminClient en mode stub — configurer rnc.keycloak.mock=false et implémenter");
        return "stub-keycloak-" + phoneNumber.hashCode();
    }

    /**
     * Active le compte utilisateur après vérification OTP.
     */
    public void activateUser(String keycloakUserId) {
        if (mockMode) {
            log.warn("[MOCK Keycloak] activateUser({})", keycloakUserId);
            return;
        }
        // TODO: kc.realm(realm).users().get(keycloakUserId).update(enabledUser);
        log.info("Activation utilisateur Keycloak {}", keycloakUserId);
    }

    /**
     * Désactive (suspend) le compte utilisateur.
     */
    public void disableUser(String keycloakUserId) {
        if (mockMode) {
            log.warn("[MOCK Keycloak] disableUser({})", keycloakUserId);
            return;
        }
        // TODO: implémentation réelle
        log.info("Suspension utilisateur Keycloak {}", keycloakUserId);
    }

    /**
     * Assigne un rôle au citoyen.
     */
    public void assignRole(String keycloakUserId, String roleName) {
        if (mockMode) {
            log.warn("[MOCK Keycloak] assignRole({},{})", keycloakUserId, roleName);
            return;
        }
        // TODO: implémentation réelle
        log.info("Assignation rôle {} à utilisateur {}", roleName, keycloakUserId);
    }
}
