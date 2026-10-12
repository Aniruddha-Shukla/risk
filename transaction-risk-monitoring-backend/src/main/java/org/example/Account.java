package org.example;

import java.math.BigDecimal;

public class Account {

    private int id;
    private String accountNumber;
    private String name;
    private BigDecimal spendingLimit;

    public Account(int id, String accountNumber, String name, BigDecimal spendingLimit) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.name = name;
        this.spendingLimit = spendingLimit;
    }

    public int getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getSpendingLimit() {
        return spendingLimit;
    }
}