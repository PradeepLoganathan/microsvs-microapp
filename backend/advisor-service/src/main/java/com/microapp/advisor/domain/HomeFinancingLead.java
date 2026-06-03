package com.microapp.advisor.domain;

/**
 * A home-financing lead raised by the customer (e.g. via the WhatsApp channel).
 * Surfaced in the app so the same customer sees their cross-channel action.
 */
public record HomeFinancingLead(
    String customerId,
    String status,        // "NONE" | "SUBMITTED"
    double amount,        // indicative amount, 0 if not specified
    String note,
    String requestedAt) {

  public static HomeFinancingLead none(String customerId) {
    return new HomeFinancingLead(customerId, "NONE", 0.0, "", "");
  }

  public HomeFinancingLead submitted(double newAmount, String newNote, String at) {
    return new HomeFinancingLead(customerId, "SUBMITTED", newAmount, newNote, at);
  }

  public boolean exists() {
    return !"NONE".equals(status);
  }
}
