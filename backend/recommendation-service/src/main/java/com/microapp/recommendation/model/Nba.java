package com.microapp.recommendation.model;

/** A single next-best-action offer surfaced at a moment of need. */
public record Nba(
    String productId,
    String headline,
    String reason,
    String cta) {}
