package com.microapp.statement.domain;

import java.util.List;

public record Statement(
    String statementId,
    String accountId,
    String periodStart,
    String periodEnd,
    double totalDebits,
    double totalCredits,
    List<Transaction> transactions
) {}
