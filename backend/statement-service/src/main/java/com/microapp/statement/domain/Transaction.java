package com.microapp.statement.domain;

public record Transaction(
    String id,
    String date,
    String merchant,
    double amount,
    String category,
    String description,
    String direction // "DEBIT" (spending) or "CREDIT" (money in)
) {

  /** Legacy 6-arg form — defaults to a debit (spending). */
  public Transaction(String id, String date, String merchant,
                     double amount, String category, String description) {
    this(id, date, merchant, amount, category, description, "DEBIT");
  }

  public boolean isCredit() {
    return "CREDIT".equals(direction);
  }
}
