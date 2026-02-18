package com.microapp.analysis.model;

public record MerchantTotal(
    String merchant,
    double total,
    int count
) {}
