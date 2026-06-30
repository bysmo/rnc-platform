package bf.rnc.services.trust.id.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Réponse après une demande d'inscription.
 */
@Getter
@Builder
public class RegistrationResponse {
    /** Référence publique du citoyen (à conserver) */
    private String citizenReference;
    /** Indique qu'un OTP a été envoyé par SMS */
    private String status;
    /** Message à afficher à l'utilisateur */
    private String message;
    /** Durée de validité de l'OTP en secondes */
    private Integer otpExpiresInSeconds;
}
