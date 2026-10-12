package org.example;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {

    private int id;
    private int accountId;
    private BigDecimal amount;
    private String merchant;
    private LocalDateTime transactionTime;

    public Transaction() {
    }

    public Transaction(int id, int accountId, BigDecimal amount,
                       String merchant, LocalDateTime transactionTime) {
        this.id = id;
        this.accountId = accountId;
        this.amount = amount;
        this.merchant = merchant;
        this.transactionTime = transactionTime;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public LocalDateTime getTransactionTime() {
        return transactionTime;
    }

    public void setTransactionTime(LocalDateTime transactionTime) {
        this.transactionTime = transactionTime;
    }
}