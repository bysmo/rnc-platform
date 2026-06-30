package bf.rnc.services.trust.id.entity;

/**
 * Types de consentement (conformité Loi 010-2004/AN).
 */
public enum ConsentType {
    /** Consentement au traitement des données personnelles (obligatoire) */
    DATA_PROCESSING,
    /** Consentement aux communications marketing (optionnel) */
    MARKETING,
    /** Consentement au partage de données avec partenaires (optionnel) */
    DATA_SHARING,
    /** Consentement au partage avec tiers spécifiques */
    THIRD_PARTY
}
