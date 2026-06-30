package bf.rnc.common.lib.exception;

public class UnauthorizedException extends RncException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, 401);
    }
}
