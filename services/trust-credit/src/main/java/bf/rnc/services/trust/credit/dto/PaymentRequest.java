package bf.rnc.services.trust.credit.dto;

import bf.rnc.services.trust.credit.entity.PaymentChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Paiement d'un crédit (webhook Mobile Money ou saisie manuelle).
 */
@Getter
@Setter
@Builder
public class PaymentRequest {

    @NotNull
    private UUID creditId;

    /** ID de l'échéance (optionnel : si null, affecté à la plus ancienne due) */
    private UUID installmentId;

    @NotNull
    @Positive
    private Long amountXof;

    @NotNull
    private PaymentChannel paymentChannel;

    private String paymentProvider;
    private String providerTransactionId;

    /** Clé d'idempotence (recommandée) — évite le double traitement */
    @NotBlank
    private String idempotencyKey;
}
