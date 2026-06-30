package bf.rnc.services.trust.id.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Génération de codes OTP à 6 chiffres.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpCodeGenerator {

    private final HashingService hashingService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Génère un code OTP à 6 chiffres.
     */
    public String generate6DigitCode() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    /**
     * Hash un code OTP pour stockage (jamais en clair en base).
     */
    public String hashOtp(String otpCode) {
        return hashingService.hash(otpCode);
    }

    /**
     * Vérifie qu'un code OTP correspond à un hash stocké.
     */
    public boolean verifyOtp(String otpCode, String storedHash) {
        return hashingService.matches(otpCode, storedHash);
    }
}
