package bf.rnc.services.trust.id.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Soumission du KYC CIN (saisie manuelle au MVP).
 */
@Getter
@Setter
@Builder
public class KycSubmissionRequest {

    @NotBlank(message = "Le numéro CIN est obligatoire")
    private String cinNumber;

    @Past(message = "La date de délivrance doit être dans le passé")
    private LocalDate cinIssueDate;

    @NotBlank(message = "Le lieu de délivrance est obligatoire")
    private String cinIssuePlace;

    private LocalDate dateOfBirth;
}
