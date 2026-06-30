package bf.rnc.common.lib.exception;

/**
 * Exception métier — mapped to HTTP 422.
 */
public class BusinessException extends RncException {
    public BusinessException(String errorCode, String message) {
        super(errorCode, message, 422);
    }
}
