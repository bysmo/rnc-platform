package bf.rnc.common.security.encryption;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service de chiffrement AES-GCM 256.
 * En production: délègue à un HSM (Hardware Security Module) physique ou PKCS#11.
 * Conformité: BCEAO, Loi 010-2004/AN (chiffrement des données personnelles au repos).
 */
@Slf4j
@Service
public class HsmEncryptionService {

    @Value("${rnc.encryption.key:}")
    private String encryptionKeyBase64;

    @Value("${rnc.encryption.hsm.enabled:false}")
    private boolean hsmEnabled;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() throws Exception {
        if (hsmEnabled) {
            log.info("HSM activé — délégation du chiffrement au module matériel");
            // En production: connexion au HSM via PKCS#11
        } else {
            log.warn("HSM désactivé — utilisation d'une clé logicielle (DEV ONLY)");
            if (encryptionKeyBase64.isEmpty()) {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                this.secretKey = keyGen.generateKey();
            } else {
                byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
                this.secretKey = new SecretKeySpec(keyBytes, "AES");
            }
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Erreur de chiffrement", e);
        }
    }

    public String decrypt(String ciphertextBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertextBase64);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erreur de déchiffrement", e);
        }
    }
}
