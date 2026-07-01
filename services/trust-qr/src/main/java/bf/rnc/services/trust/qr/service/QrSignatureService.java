package bf.rnc.services.trust.qr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Service de signature et chiffrement des payloads QR Code.
 *
 * <p>Au MVP, on utilise HMAC-SHA256 avec un secret partagé.
 * En production : ECDSA avec clé privée stockée dans HSM.</p>
 */
@Slf4j
@Service
public class QrSignatureService {

    @Value("${rnc.qr.signature-key:dev-qr-signature-key-change-in-production}")
    private String signatureKey;

    @Value("${rnc.qr.encryption-key:dev-qr-encryption-key-change-in-production}")
    private String encryptionKey;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Signe un payload avec HMAC-SHA256.
     */
    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(signatureKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Erreur signature", e);
        }
    }

    /**
     * Vérifie qu'un payload correspond à une signature.
     */
    public boolean verify(String payload, String signature) {
        String expected = sign(payload);
        return expected.equals(signature);
    }

    /**
     * Hash SHA-256 du payload (pour recherche / index).
     */
    public String hash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erreur hash", e);
        }
    }

    /**
     * Chiffre le payload (obfuscation simple XOR + Base64 pour MVP).
     * En production : AES-256-GCM.
     */
    public String encrypt(String payload) {
        try {
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[payloadBytes.length];
            for (int i = 0; i < payloadBytes.length; i++) {
                result[i] = (byte) (payloadBytes[i] ^ keyBytes[i % keyBytes.length]);
            }
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Erreur chiffrement", e);
        }
    }

    /**
     * Déchiffre le payload.
     */
    public String decrypt(String encrypted) {
        try {
            byte[] data = Base64.getDecoder().decode(encrypted);
            byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                result[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
            }
            return new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erreur déchiffrement", e);
        }
    }

    /**
     * Génère un nonce aléatoire (pour QR dynamiques).
     */
    public String generateNonce(int length) {
        byte[] nonce = new byte[length];
        secureRandom.nextBytes(nonce);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    }
}
