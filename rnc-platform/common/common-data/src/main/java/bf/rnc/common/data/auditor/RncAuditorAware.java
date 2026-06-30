package bf.rnc.common.data.auditor;

import bf.rnc.common.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * AuditorAware — injecte l'utilisateur courant dans les champs createdBy/updatedBy.
 */
public class RncAuditorAware implements AuditorAware<String> {

    @Autowired(required = false)
    private SecurityContextHelper securityContextHelper;

    @Override
    public Optional<String> getCurrentAuditor() {
        if (securityContextHelper == null) return Optional.of("system");
        String userId = securityContextHelper.getCurrentUserId();
        return Optional.ofNullable(userId);
    }
}
