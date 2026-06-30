package bf.rnc.services.trust.id.entity;

/**
 * Statut PEP (Politically Exposed Person) — conformité LCB-FT.
 */
public enum PepStatus {
    /** Vérification non effectuée */
    NOT_CHECKED,
    /** Le citoyen n'est pas une PEP */
    NOT_PEP,
    /** Le citoyen est lui-même une PEP */
    PEP,
    /** Un membre proche de la famille est PEP */
    PEP_FAMILY
}
