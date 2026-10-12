package org.example;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionRepository {

    public record FlaggedTransaction(int transactionId, int accountId, String accountNumber, String accountName,
                                     BigDecimal amount, String merchant, LocalDateTime transactionTime,
                                     int score, String riskLevel, List<String> reasons) {
    }

    private static final String REASON_SEPARATOR = "; ";

    public List<Transaction> getAllTransactions() throws SQLException {

        List<Transaction> transactions = new ArrayList<>();

        String sql = "SELECT id, account_id, amount, merchant, transaction_time " +
                "FROM transactions ORDER BY transaction_time DESC";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {

                Transaction transaction = new Transaction();

                transaction.setId(resultSet.getInt("id"));
                transaction.setAccountId(resultSet.getInt("account_id"));
                transaction.setAmount(resultSet.getBigDecimal("amount"));
                transaction.setMerchant(resultSet.getString("merchant"));

                if (resultSet.getTimestamp("transaction_time") != null) {
                    transaction.setTransactionTime(
                            resultSet.getTimestamp("transaction_time").toLocalDateTime()
                    );
                }

                transactions.add(transaction);
            }
        }

        return transactions;
    }

    /** Account's past spending, used by the risk engine before the new transaction is saved. */
    public RiskService.SpendingHistory getSpendingHistory(int accountId) throws SQLException {

        String sql = "SELECT COUNT(*) AS total, COALESCE(AVG(amount), 0) AS average, " +
                "COUNT(*) FILTER (WHERE transaction_time > NOW() - (? * INTERVAL '1 minute')) AS recent " +
                "FROM transactions WHERE account_id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, RiskService.VELOCITY_WINDOW_MINUTES);
            statement.setInt(2, accountId);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return new RiskService.SpendingHistory(
                        resultSet.getInt("total"),
                        resultSet.getBigDecimal("average"),
                        resultSet.getInt("recent")
                );
            }
        }
    }

    /** Inserts the transaction and its risk score atomically; fills in the generated id and timestamp. */
    public Transaction saveWithRisk(Transaction transaction, RiskService.RiskAssessment risk) throws SQLException {

        String insertTransaction = "INSERT INTO transactions (account_id, amount, merchant) VALUES (?, ?, ?) " +
                "RETURNING id, transaction_time";
        String insertRisk = "INSERT INTO risk_scores (transaction_id, score, risk_level, flagged, reasons) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection()) {

            connection.setAutoCommit(false);

            try (PreparedStatement transactionStatement = connection.prepareStatement(insertTransaction);
                 PreparedStatement riskStatement = connection.prepareStatement(insertRisk)) {

                transactionStatement.setInt(1, transaction.getAccountId());
                transactionStatement.setBigDecimal(2, transaction.getAmount());
                transactionStatement.setString(3, transaction.getMerchant());

                try (ResultSet resultSet = transactionStatement.executeQuery()) {
                    resultSet.next();
                    transaction.setId(resultSet.getInt("id"));
                    transaction.setTransactionTime(resultSet.getTimestamp("transaction_time").toLocalDateTime());
                }

                riskStatement.setInt(1, transaction.getId());
                riskStatement.setInt(2, risk.score());
                riskStatement.setString(3, risk.riskLevel());
                riskStatement.setBoolean(4, risk.flagged());
                riskStatement.setString(5, String.join(REASON_SEPARATOR, risk.reasons()));
                riskStatement.executeUpdate();

                connection.commit();
                return transaction;

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public List<FlaggedTransaction> getFlaggedTransactions() throws SQLException {

        List<FlaggedTransaction> flagged = new ArrayList<>();

        String sql = "SELECT t.id, t.account_id, a.account_number, a.name, t.amount, t.merchant, " +
                "t.transaction_time, r.score, r.risk_level, r.reasons " +
                "FROM risk_scores r " +
                "JOIN transactions t ON t.id = r.transaction_id " +
                "JOIN accounts a ON a.id = t.account_id " +
                "WHERE r.flagged " +
                "ORDER BY t.transaction_time DESC LIMIT 100";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {

                String reasons = resultSet.getString("reasons");

                flagged.add(new FlaggedTransaction(
                        resultSet.getInt("id"),
                        resultSet.getInt("account_id"),
                        resultSet.getString("account_number"),
                        resultSet.getString("name"),
                        resultSet.getBigDecimal("amount"),
                        resultSet.getString("merchant"),
                        resultSet.getTimestamp("transaction_time").toLocalDateTime(),
                        resultSet.getInt("score"),
                        resultSet.getString("risk_level"),
                        reasons == null || reasons.isBlank() ? List.of() : List.of(reasons.split(REASON_SEPARATOR))
                ));
            }
        }

        return flagged;
    }
}
