package bf.rnc.common.lib.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Génération d'identifiants uniques — utilisant SecureRandom pour éviter l'énumération.
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private IdGenerator() {}

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Génère un code court lisible (ex: pour QR codes, reconnaissances de dette).
     */
    public static String shortCode(int length) {
        char[] buf = new char[length];
        for (int i = 0; i < length; i++) {
            buf[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }

    /**
     * Génère un identifiant préfixé (ex: TRS-XXXX pour transaction).
     */
    public static String prefixed(String prefix, int randomLength) {
        return prefix + "-" + shortCode(randomLength);
    }
}
