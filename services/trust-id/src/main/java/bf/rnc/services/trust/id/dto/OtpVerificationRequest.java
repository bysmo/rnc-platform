package bf.rnc.services.trust.id.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OtpVerificationRequest {

    @NotBlank(message = "La référence citoyen est obligatoire")
    private String citizenReference;

    @NotBlank(message = "Le code OTP est obligatoire")
    @Size(min = 6, max = 6, message = "Le code OTP doit comporter 6 chiffres")
    private String otpCode;
}
