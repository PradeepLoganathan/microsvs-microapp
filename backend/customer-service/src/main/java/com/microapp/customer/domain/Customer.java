package com.microapp.customer.domain;

/**
 * Customer lifecycle state. One id evolves through three stages
 * (VISITOR → REGISTERED → CUSTOMER) — no migration, the event-sourcing proof.
 * Pure domain — no Akka dependencies. Mutations return new instances.
 */
public record Customer(
    String customerId,
    Stage stage,
    String email,
    String phone,
    String channel,
    WelcomeOffer offer,
    String accountId,
    String kycRef,
    long createdAt,
    long updatedAt) {

  public enum Stage {
    VISITOR,
    REGISTERED,
    CUSTOMER
  }

  /** Personalized welcome offer, computed on registration (see WelcomeOfferRules). */
  public record WelcomeOffer(
      String code, String title, String description, String starterProductId, double bonusAmount) {}

  /** Registration command payload. */
  public record Registration(String email, String phone, String channel) {
    public boolean isValid() {
      return notBlank(email) && notBlank(phone);
    }

    private static boolean notBlank(String s) {
      return s != null && !s.isBlank();
    }
  }

  public static Customer visitor(String customerId, String channel, long at) {
    return new Customer(customerId, Stage.VISITOR, null, null, channel, null, null, null, at, at);
  }

  public Customer registered(
      String email, String phone, String channel, WelcomeOffer offer, String accountId, long at) {
    return new Customer(
        customerId, Stage.REGISTERED, email, phone, channel, offer, accountId, kycRef, createdAt, at);
  }

  public Customer withKyc(String kycRef, long at) {
    return new Customer(
        customerId, Stage.CUSTOMER, email, phone, channel, offer, accountId, kycRef, createdAt, at);
  }
}
