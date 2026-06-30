package bf.rnc.services.trust.id.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Service de hachage pour données personnelles.
 * Utilise SHA-256 avec un pepper secret pour permettre la recherche exacte
 * sans jamais stocker la donnée en clair.
 *
 * <p>Conformité Loi 010-2004/AN : le pepper doit être stocké dans un coffre-fort
 * (Vault, HSM) et jamais dans le code source.</p>
 */
@Slf4j
@Service
public class HashingService {

    @Value("${rnc.security.pepper:dev-pepper-change-in-production}")
    private String pepper;

    /**
     * Hash SHA-256 d'une donnée avec pepper.
     *
     * @param data donnée à hasher (ex: +226XXXXXXXX)
     * @return hash hex 64 caractères
     */
    public String hash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = pepper + ":" + data;
            byte[] hashBytes = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }

    /**
     * Vérifie qu'une donnée correspond à un hash.
     */
    public boolean matches(String data, String hash) {
        return hash(data).equals(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Encode une chaîne en Base64 (utile pour stockage intermédiaire).
     */
    public String base64Encode(String data) {
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Décode une chaîne Base64.
     */
    public String base64Decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
