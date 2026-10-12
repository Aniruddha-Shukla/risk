package org.example;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Connection settings come from the environment, resolved once at startup:
 * 1. DATABASE_URL - e.g. Neon's postgresql://user:pass@host/db?sslmode=require (a jdbc: URL also works)
 * 2. otherwise DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD (defaults point at a local Postgres)
 */
public class DatabaseConnection {

    private record Settings(String url, String user, String password) {
    }

    private static final Settings SETTINGS = resolveSettings();

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(SETTINGS.url(), SETTINGS.user(), SETTINGS.password());
    }

    public static String describe() {
        // Safe to log: the JDBC URL never contains the password
        return SETTINGS.url().replaceAll("\\?.*", "");
    }

    private static Settings resolveSettings() {
        String databaseUrl = System.getenv("DATABASE_URL");

        if (databaseUrl == null || databaseUrl.isBlank()) {
            String url = "jdbc:postgresql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "5432")
                    + "/" + env("DB_NAME", "risk_monitoring");
            return new Settings(url, env("DB_USER", "postgres"), env("DB_PASSWORD", ""));
        }

        if (databaseUrl.startsWith("jdbc:")) {
            return new Settings(databaseUrl, System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
        }

        // postgres://user:pass@host[:port]/db?params -> jdbc:postgresql://host[:port]/db?params + credentials
        URI uri = URI.create(databaseUrl.trim());
        String user = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            user = decode(parts[0]);
            password = parts.length > 1 ? decode(parts[1]) : null;
        }
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();

        return new Settings("jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath() + query, user, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
