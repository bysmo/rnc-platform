package bf.rnc.services.trust.qr.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Génération d'images QR Code via ZXing.
 */
@Slf4j
@Service
public class QrImageGenerator {

    private static final int DEFAULT_WIDTH = 400;
    private static final int DEFAULT_HEIGHT = 400;

    /**
     * Génère une image PNG en Base64 à partir d'un payload.
     */
    public String generateBase64(String payload) {
        return generateBase64(payload, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public String generateBase64(String payload, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, width, height, hints);

            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);

            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            log.debug("QR généré: {}x{} — {} caractères base64", width, height, base64.length());
            return base64;

        } catch (Exception e) {
            log.error("Erreur génération QR", e);
            throw new RuntimeException("Échec génération QR Code", e);
        }
    }
}
