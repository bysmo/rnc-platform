package bf.rnc.common.lib.exception;

import lombok.Getter;

/**
 * Exception racine de la plateforme RNC.
 */
@Getter
public class RncException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public RncException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = 500;
    }

    public RncException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public RncException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = 500;
    }
}
