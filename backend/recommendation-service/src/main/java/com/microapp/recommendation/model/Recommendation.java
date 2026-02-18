package com.microapp.recommendation.model;

public record Recommendation(
    String productId,
    String productName,
    String reason
) {}
