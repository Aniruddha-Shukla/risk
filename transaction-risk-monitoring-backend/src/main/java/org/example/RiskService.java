package org.example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based risk engine. Every rule that fires adds points; the total is capped at 100.
 * Scores at or above the flag threshold (default 50) are HIGH risk and flagged for review.
 *
 *   SPENDING_LIMIT   +50  amount exceeds the account's spending limit
 *   UNUSUAL_AMOUNT   +30  amount exceeds N x the account's historical average (needs 3+ past transactions)
 *   HIGH_VELOCITY    +20  N or more earlier transactions on the account in the last 10 minutes
 *   LARGE_AMOUNT     +20  amount is at or above an absolute large-transaction threshold
 */
public class RiskService {

    public record SpendingHistory(int transactionCount, BigDecimal averageAmount, int recentTransactionCount) {
    }

    public record RiskAssessment(int score, String riskLevel, boolean flagged, List<String> reasons) {
    }

    public static final int VELOCITY_WINDOW_MINUTES = 10;
    private static final int MIN_HISTORY_FOR_PATTERN = 3;

    private final BigDecimal largeAmount;
    private final BigDecimal averageMultiplier;
    private final int velocityLimit;
    private final int flagThreshold;

    public RiskService(BigDecimal largeAmount, BigDecimal averageMultiplier, int velocityLimit, int flagThreshold) {
        this.largeAmount = largeAmount;
        this.averageMultiplier = averageMultiplier;
        this.velocityLimit = velocityLimit;
        this.flagThreshold = flagThreshold;
    }

    public static RiskService fromEnv() {
        return new RiskService(
                new BigDecimal(env("RISK_LARGE_AMOUNT", "50000")),
                new BigDecimal(env("RISK_AVG_MULTIPLIER", "3")),
                Integer.parseInt(env("RISK_VELOCITY_LIMIT", "3")),
                Integer.parseInt(env("RISK_FLAG_THRESHOLD", "50"))
        );
    }

    public RiskAssessment assess(BigDecimal amount, BigDecimal spendingLimit, SpendingHistory history) {

        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (amount.compareTo(spendingLimit) > 0) {
            score += 50;
            reasons.add("Exceeds account spending limit of " + spendingLimit.toPlainString());
        }

        if (history.transactionCount() >= MIN_HISTORY_FOR_PATTERN
                && amount.compareTo(history.averageAmount().multiply(averageMultiplier)) > 0) {
            score += 30;
            reasons.add("Over " + averageMultiplier.toPlainString() + "x the account's average of "
                    + history.averageAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        }

        if (history.recentTransactionCount() >= velocityLimit) {
            score += 20;
            reasons.add(history.recentTransactionCount() + " other transactions in the last "
                    + VELOCITY_WINDOW_MINUTES + " minutes");
        }

        if (amount.compareTo(largeAmount) >= 0) {
            score += 20;
            reasons.add("At or above large-transaction threshold of " + largeAmount.toPlainString());
        }

        score = Math.min(score, 100);
        boolean flagged = score >= flagThreshold;
        String riskLevel = flagged ? "HIGH" : score >= 25 ? "MEDIUM" : "LOW";

        return new RiskAssessment(score, riskLevel, flagged, reasons);
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
