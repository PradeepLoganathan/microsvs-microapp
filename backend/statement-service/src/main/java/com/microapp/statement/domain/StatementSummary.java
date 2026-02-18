package com.microapp.statement.domain;

public record StatementSummary(
    String statementId,
    String accountId,
    String periodStart,
    String periodEnd,
    double totalDebits,
    int transactionCount
) {}
