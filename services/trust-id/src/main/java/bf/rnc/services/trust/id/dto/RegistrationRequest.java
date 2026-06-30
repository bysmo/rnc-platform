package bf.rnc.services.trust.id.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Demande d'inscription d'un citoyen.
 */
@Getter
@Setter
@Builder
public class RegistrationRequest {

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(regexp = "^\\+226\\d{8}$", message = "Format attendu: +226XXXXXXXX (8 chiffres après l'indicatif)")
    private String phoneNumber;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100, message = "Le prénom ne peut pas dépasser 100 caractères")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
    private String lastName;

    @Pattern(regexp = "^[MFO]$", message = "Genre: M, F ou O")
    private String gender;

    @NotBlank(message = "La région est obligatoire")
    private String region;

    @NotBlank(message = "La province est obligatoire")
    private String province;

    @NotBlank(message = "La commune est obligatoire")
    private String commune;

    private String village;

    @NotNull(message = "Le consentement au traitement des données est obligatoire (Loi 010-2004/AN)")
    private Boolean consentDataProcessing;

    private Boolean consentMarketing = false;
    private Boolean consentDataSharing = false;

    @Builder.Default
    private String preferredLanguage = "fr";
}
