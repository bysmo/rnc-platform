package bf.rnc.services.trust.credit.dto;

import bf.rnc.services.trust.credit.entity.CreditPurpose;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Demande de nano-crédit par un citoyen.
 */
@Getter
@Setter
@Builder
public class CreditRequest {

    @NotBlank(message = "L'identifiant citoyen est obligatoire")
    private String citizenId;

    @NotNull(message = "Le montant est obligatoire")
    @Positive(message = "Le montant doit être positif")
    @Max(value = 500_000, message = "Le montant maximum est 500 000 XOF")
    private Long amountXof;

    @NotNull(message = "La durée est obligatoire")
    @Min(value = 7, message = "La durée minimum est 7 jours")
    @Max(value = 365, message = "La durée maximum est 365 jours")
    private Integer durationDays;

    @NotNull(message = "L'objet du crédit est obligatoire")
    private CreditPurpose purpose;

    /** Taux d'intérêt en bps (100 = 1%). 0 = sans intérêt */
    @Min(0)
    @Max(5000)
    @Builder.Default
    private Integer interestRateBps = 0;

    /** ID marchand si crédit affecté */
    private String merchantId;

    /** Numéro Mobile Money pour déblocage/remboursement */
    private String mobileMoneyAccount;
}
