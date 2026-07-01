package bf.rnc.services.trust.score.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Contestation de score déposée par un citoyen.
 */
@Getter
@Setter
@Builder
public class ScoreAppealRequest {

    @NotBlank
    private String citizenId;

    @NotBlank
    @Size(min = 10, max = 1000, message = "La raison doit contenir entre 10 et 1000 caractères")
    private String reason;
}
