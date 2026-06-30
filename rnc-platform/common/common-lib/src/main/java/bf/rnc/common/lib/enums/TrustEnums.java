package bf.rnc.common.lib.enums;

/**
 * Énumérations communes RNC.
 */
public final class TrustEnums {

    private TrustEnums() {}

    public enum UserRole {
        CITIZEN, MERCHANT, BANK_AGENT, INSURANCE_AGENT,
        SCHOOL_ADMIN, HEALTH_ADMIN, FARMING_COOP,
        COLLECTOR, AUDITOR, ADMIN, SYSTEM
    }

    public enum TrustScoreLevel {
        CRITICAL(0, 299),
        LOW(300, 499),
        FAIR(500, 649),
        GOOD(650, 799),
        EXCELLENT(800, 1000);

        private final int min;
        private final int max;

        TrustScoreLevel(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public static TrustScoreLevel fromScore(int score) {
            for (TrustScoreLevel level : values()) {
                if (score >= level.min && score <= level.max) return level;
            }
            return CRITICAL;
        }
    }

    public enum CreditStatus {
        REQUESTED, ANALYZED, APPROVED, REJECTED,
        DISBURSED, ACTIVE, COMPLETED, DEFAULTED, CANCELLED
    }

    public enum EscrowStatus {
        RESERVED, PARTIALLY_RELEASED, FULLY_RELEASED,
        REFUNDED, DISPUTED
    }

    public enum DebtStatus {
        DRAFT, SIGNED, ACTIVE, HONORED, PARTIALLY_HONORED,
        OVERDUE, DEFAULTED, DISPUTED, CANCELLED
    }

    public enum MerchantCategory {
        SCHOOL, HEALTH, AGRICULTURE, INSURANCE, RETAIL, SERVICE
    }

    public enum PaymentChannel {
        MOBILE_MONEY, BANK_TRANSFER, ESCROW_RELEASE, CASH
    }

    public enum ComplianceFlag {
        KYC_VERIFIED, LCB_FT_CHECKED, BCEAO_COMPLIANT,
        DATA_PROTECTION_COMPLIANT, SANCTIONS_SCREENED
    }
}
