package bf.rnc.services.trust.id.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service d'envoi de SMS pour OTP et notifications.
 *
 * <p>Stratégie multi-niveaux :</p>
 * <ul>
 *   <li><b>MOCK</b> (dev only) : journalise le code dans la console</li>
 *   <li><b>Orange Money API</b> : intégration future (TODO)</li>
 *   <li><b>Moov Money API</b> : intégration future</li>
 *   <li><b>Telecel Money API</b> : intégration future</li>
 * </ul>
 */
@Slf4j
@Service
public class SmsOtpService {

    @Value("${rnc.sms.mock:true}")
    private boolean mockMode;

    @Value("${rnc.sms.sender:RNC}")
    private String sender;

    @Value("${rnc.sms.provider:mock}")
    private String provider;

    /**
     * Envoie un code OTP par SMS.
     *
     * @param phoneNumber numéro au format +226XXXXXXXX
     * @param otpCode     code à 6 chiffres
     * @return true si l'envoi a réussi
     */
    public boolean sendOtp(String phoneNumber, String otpCode) {
        if (mockMode) {
            log.warn("╔════════════════════════════════════════════════╗");
            log.warn("║ [MOCK SMS] OTP envoyé à {}               ", phoneNumber);
            log.warn("║ Code OTP : {}                                  ", otpCode);
            log.warn("║ Expéditeur: {}                                  ", sender);
            log.warn("╚════════════════════════════════════════════════╝");
            return true;
        }

        // TODO: intégration réelle selon provider
        // switch (provider) {
        //     case "orange": return sendViaOrangeApi(phoneNumber, otpCode);
        //     case "moov": return sendViaMoovApi(phoneNumber, otpCode);
        //     case "telecel": return sendViaTelecelApi(phoneNumber, otpCode);
        //     default: throw new IllegalStateException("Provider SMS inconnu: " + provider);
        // }

        log.error("Provider SMS non configuré — set rnc.sms.mock=true pour le dev");
        return false;
    }

    /**
     * Envoie une notification SMS générique.
     */
    public boolean sendNotification(String phoneNumber, String message) {
        if (mockMode) {
            log.warn("[MOCK SMS] Notif → {} : {}", phoneNumber, message);
            return true;
        }
        // TODO: implémentation réelle
        return false;
    }
}
