package bf.rnc.services.trust.score.entity;

/**
 * Source d'une entrée d'historique de score.
 */
public enum ScoreHistorySource {
    /** Calcul automatique par l'algorithme */
    COMPUTED,
    /** Ajustement manuel par un administrateur */
    MANUAL_ADJUSTMENT,
    /** Révision suite à contestation citoyen */
    APPEAL,
    /** Score initial à l'inscription */
    INITIAL
}
