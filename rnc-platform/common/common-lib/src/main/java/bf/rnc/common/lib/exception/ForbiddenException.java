package bf.rnc.common.lib.exception;

public class ForbiddenException extends RncException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, 403);
    }
}
