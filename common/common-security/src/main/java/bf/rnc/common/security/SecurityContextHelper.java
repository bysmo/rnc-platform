package bf.rnc.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Helper pour récupérer l'utilisateur courant depuis le JWT Keycloak.
 */
@Component
public class SecurityContextHelper {

    public Optional<Jwt> getCurrentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    public String getCurrentUserId() {
        return getCurrentJwt()
                .map(jwt -> jwt.getClaimAsString("sub"))
                .orElse("anonymous");
    }

    public String getCurrentUserEmail() {
        return getCurrentJwt()
                .map(jwt -> jwt.getClaimAsString("email"))
                .orElse(null);
    }

    public String getCurrentUserPreferredUsername() {
        return getCurrentJwt()
                .map(jwt -> jwt.getClaimAsString("preferred_username"))
                .orElse(null);
    }

    public boolean hasRole(String role) {
        return getCurrentJwt()
                .map(jwt -> {
                    Object realmAccess = jwt.getClaim("realm_access");
                    if (realmAccess instanceof java.util.Map<?, ?> ra) {
                        Object roles = ra.get("roles");
                        return roles instanceof java.util.Collection<?> c && c.contains(role);
                    }
                    return false;
                })
                .orElse(false);
    }
}
