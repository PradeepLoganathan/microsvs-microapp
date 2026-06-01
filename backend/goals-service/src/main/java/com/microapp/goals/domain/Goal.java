package com.microapp.goals.domain;

/**
 * A savings goal ("tabung") owned by a customer. Pure domain — no Akka deps.
 * Mutations return new instances; the entity decides which events to persist.
 */
public record Goal(
    String goalId,
    String customerId,
    String name,
    Category category,
    double targetAmount,
    double currentAmount,
    String targetDate,
    Status status,
    long createdAt,
    long updatedAt) {

  public enum Category {
    HAJJ,
    HOLIDAY,
    HOUSE,
    EMERGENCY,
    EDUCATION,
    OTHER
  }

  public enum Status {
    ACTIVE,
    COMPLETED,
    ABANDONED
  }

  /** Initial-create payload — keeps the entity command shape small. */
  public record Spec(
      String name, Category category, double targetAmount, String targetDate) {
    public boolean isValid() {
      return notBlank(name) && category != null && targetAmount > 0 && notBlank(targetDate);
    }

    private static boolean notBlank(String s) {
      return s != null && !s.isBlank();
    }
  }

  public static Goal create(String goalId, String customerId, Spec spec, long at) {
    return new Goal(
        goalId,
        customerId,
        spec.name(),
        spec.category(),
        spec.targetAmount(),
        0.0,
        spec.targetDate(),
        Status.ACTIVE,
        at,
        at);
  }

  public Goal contribute(double amount, long at) {
    return new Goal(
        goalId,
        customerId,
        name,
        category,
        targetAmount,
        currentAmount + amount,
        targetDate,
        status,
        createdAt,
        at);
  }

  public Goal complete(long at) {
    return new Goal(
        goalId,
        customerId,
        name,
        category,
        targetAmount,
        currentAmount,
        targetDate,
        Status.COMPLETED,
        createdAt,
        at);
  }

  public Goal abandon(long at) {
    return new Goal(
        goalId,
        customerId,
        name,
        category,
        targetAmount,
        currentAmount,
        targetDate,
        Status.ABANDONED,
        createdAt,
        at);
  }

  public boolean isActive() {
    return status == Status.ACTIVE;
  }

  public boolean isReached() {
    return currentAmount >= targetAmount;
  }

  public double progress() {
    if (targetAmount <= 0) return 0.0;
    double p = currentAmount / targetAmount;
    return p > 1.0 ? 1.0 : p;
  }

  public double remaining() {
    double r = targetAmount - currentAmount;
    return r < 0 ? 0.0 : r;
  }
}
