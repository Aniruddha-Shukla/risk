package org.example;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.Map;
import java.util.TimeZone;

public class Main {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    public static void main(String[] args) {

        // Same clock everywhere (laptop or Render): every timestamp the API returns is UTC
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // Test PostgreSQL connection (non-fatal, so /health still comes up and the logs show the cause)
        try (Connection connection = DatabaseConnection.getConnection()) {

            System.out.println("Database connected successfully: " + DatabaseConnection.describe());

        } catch (Exception e) {

            System.out.println("Database connection failed (" + DatabaseConnection.describe() + "): " + e.getMessage());
        }

        AccountRepository accountRepository = new AccountRepository();
        TransactionRepository transactionRepository = new TransactionRepository();
        RiskService riskService = RiskService.fromEnv();

        Javalin app = Javalin.create(config -> {

            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> mapper
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)));

            // CORS_ORIGINS: comma-separated frontend origins, e.g. https://my-app.vercel.app (unset = allow any)
            String corsOrigins = System.getenv("CORS_ORIGINS");
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                if (corsOrigins == null || corsOrigins.isBlank()) {
                    rule.anyHost();
                    return;
                }
                for (String origin : corsOrigins.split(",")) {
                    if (!origin.isBlank()) {
                        rule.allowHost(origin.trim().replaceAll("/+$", ""));
                    }
                }
            }));
        });

        app.exception(HttpResponseException.class, (e, ctx) ->
                ctx.status(e.getStatus()).json(Map.of("error", e.getMessage())));

        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Internal server error"));
        });

        app.get("/health", ctx -> ctx.json(Map.of("status", "UP")));

        app.get("/api/accounts", ctx -> ctx.json(accountRepository.getAllAccounts()));

        app.get("/api/transactions", ctx -> ctx.json(transactionRepository.getAllTransactions()));

        app.get("/api/transactions/flagged", ctx -> ctx.json(transactionRepository.getFlaggedTransactions()));

        app.post("/api/transactions", ctx -> {

            Transaction transaction = parseTransaction(ctx);

            Account account = accountRepository.findById(transaction.getAccountId())
                    .orElseThrow(() -> new NotFoundResponse("Account " + transaction.getAccountId() + " not found"));

            RiskService.RiskAssessment risk = riskService.assess(
                    transaction.getAmount(),
                    account.getSpendingLimit(),
                    transactionRepository.getSpendingHistory(account.getId())
            );

            Transaction saved = transactionRepository.saveWithRisk(transaction, risk);

            ctx.status(HttpStatus.CREATED).json(Map.of("transaction", saved, "risk", risk));
        });

        // Render injects PORT; 8080 locally
        app.start(Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")));
    }

    private static Transaction parseTransaction(Context ctx) {

        Transaction transaction;
        try {
            transaction = ctx.bodyAsClass(Transaction.class);
        } catch (Exception e) {
            throw new BadRequestResponse("Body must be JSON like {\"accountId\": 1, \"amount\": 250.00, \"merchant\": \"Amazon\"}");
        }

        if (transaction.getAccountId() <= 0) {
            throw new BadRequestResponse("accountId is required");
        }

        BigDecimal amount = transaction.getAmount() == null ? null : transaction.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BadRequestResponse("amount must be between 0.01 and " + MAX_AMOUNT.toPlainString());
        }

        String merchant = transaction.getMerchant() == null ? "" : transaction.getMerchant().trim();
        if (merchant.isEmpty() || merchant.length() > 100) {
            throw new BadRequestResponse("merchant is required (max 100 characters)");
        }

        transaction.setAmount(amount);
        transaction.setMerchant(merchant);
        return transaction;
    }
}
