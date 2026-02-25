package com.microapp.product.application;

public record ProductSummary(
    String productId,
    String productName,
    String category,
    String description,
    String eligibility
) {}
