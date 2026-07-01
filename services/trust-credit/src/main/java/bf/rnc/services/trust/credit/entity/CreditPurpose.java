package bf.rnc.services.trust.credit.entity;

/**
 * Objet du financement — impacte les règles de déblocage escrow.
 */
public enum CreditPurpose {
    /** Frais de scolarité — déblocage direct à l'école */
    SCHOOL,
    /** Soins médicaux — déblocage au centre de santé / pharmacie */
    HEALTH,
    /** Campagne agricole — déblocage échelonné aux fournisseurs */
    FARMING,
    /** Achat chez un commerçant agréé */
    MERCHANT,
    /** Consolidation de dettes entre particuliers */
    DEBT_CONSOLIDATION,
    /** Urgence familiale (maladie, deuil) */
    EMERGENCY,
    /** Équipement professionnel */
    EQUIPMENT,
    /** Autre objet — analyse manuelle obligatoire */
    OTHER
}
