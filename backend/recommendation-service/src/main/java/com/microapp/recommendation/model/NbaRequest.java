package com.microapp.recommendation.model;

/**
 * Flat request shape — the trigger is required; the rest of the fields describe
 * the moment context. All non-trigger fields are optional; the engine reads
 * whatever is present. Client decides which trigger to fire based on what the
 * user is looking at.
 */
public record NbaRequest(
    String trigger,
    String transactionId,
    Double amount,
    String merchant,
    String category,
    Boolean overseas) {

  public static final String TRIGGER_LARGE_TXN = "LARGE_TRANSACTION_VIEWED";
  public static final String TRIGGER_SALARY_CREDIT = "SALARY_CREDIT_VIEWED";
}
