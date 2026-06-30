package bf.rnc.common.lib.exception;

public class NotFoundException extends RncException {
    public NotFoundException(String resource, String id) {
        super("NOT_FOUND", resource + " introuvable pour l'identifiant: " + id, 404);
    }
}
