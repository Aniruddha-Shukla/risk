package org.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccountRepository {

    public List<Account> getAllAccounts() throws SQLException {

        List<Account> accounts = new ArrayList<>();

        String sql = "SELECT id, account_number, name, spending_limit FROM accounts ORDER BY id";

        try (
                Connection connection = DatabaseConnection.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {

            while (resultSet.next()) {
                accounts.add(mapAccount(resultSet));
            }
        }

        return accounts;
    }

    public Optional<Account> findById(int id) throws SQLException {

        String sql = "SELECT id, account_number, name, spending_limit FROM accounts WHERE id = ?";

        try (
                Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapAccount(resultSet)) : Optional.empty();
            }
        }
    }

    private Account mapAccount(ResultSet resultSet) throws SQLException {
        return new Account(
                resultSet.getInt("id"),
                resultSet.getString("account_number"),
                resultSet.getString("name"),
                resultSet.getBigDecimal("spending_limit")
        );
    }
}
