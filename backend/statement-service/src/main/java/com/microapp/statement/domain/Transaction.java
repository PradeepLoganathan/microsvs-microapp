package com.microapp.statement.domain;

public record Transaction(
    String id,
    String date,
    String merchant,
    double amount,
    String category,
    String description
) {}
