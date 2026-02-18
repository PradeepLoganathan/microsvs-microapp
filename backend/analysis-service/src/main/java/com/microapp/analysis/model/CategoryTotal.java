package com.microapp.analysis.model;

public record CategoryTotal(
    String category,
    double total,
    int count,
    double percentage
) {}
