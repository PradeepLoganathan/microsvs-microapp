package com.microapp.advisor.domain;

/**
 * Result of an affordability check: can a customer reach {@code targetAmount}
 * in {@code months} given their available monthly surplus?
 *
 * Pure domain logic — no Akka dependencies. The {@link #project} factory holds
 * the calculation; the agent's {@code projectAffordability} tool calls it.
 */
public record AffordabilityProjection(
    boolean feasible,
    double targetAmount,
    int months,
    double requiredMonthly,
    double availableMonthly,
    double shortfallMonthly,
    String explanation) {

  /**
   * Compute whether saving {@code monthlySurplus} per month reaches
   * {@code targetAmount} within {@code months}.
   */
  public static AffordabilityProjection project(double targetAmount, int months, double monthlySurplus) {
    if (targetAmount <= 0) {
      throw new IllegalArgumentException("targetAmount must be > 0");
    }
    if (months <= 0) {
      throw new IllegalArgumentException("months must be > 0");
    }

    double required = round2(targetAmount / months);
    double available = round2(monthlySurplus);
    boolean feasible = available >= required;
    double shortfall = feasible ? 0.0 : round2(required - available);

    String explanation = feasible
        ? "Saving %.2f/month for %d months reaches %.2f. About %.2f/month is available, so this is achievable."
            .formatted(required, months, round2(targetAmount), available)
        : "Reaching %.2f in %d months needs %.2f/month, but only about %.2f/month is available — a shortfall of %.2f/month."
            .formatted(round2(targetAmount), months, required, available, shortfall);

    return new AffordabilityProjection(feasible, round2(targetAmount), months, required, available, shortfall, explanation);
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
