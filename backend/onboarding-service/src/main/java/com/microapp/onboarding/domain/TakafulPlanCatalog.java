package com.microapp.onboarding.domain;

import java.util.List;
import java.util.Optional;

/**
 * Static catalog of Shariah-compliant Takaful plans the user can choose from.
 * Kept local to onboarding-service (no product-service coupling) for a self-contained demo.
 */
public final class TakafulPlanCatalog {

  public record Plan(String id, String name, String description, double minMonthlyContribution) {}

  public static final List<Plan> PLANS = List.of(
      new Plan("family-protect", "Family Protect Takaful",
          "Shariah-compliant family protection with a death/disability benefit.", 150.0),
      new Plan("medical-care", "Medical Care Takaful",
          "Covers hospitalisation and surgical costs with takaful surplus sharing.", 200.0),
      new Plan("legacy-savings", "Legacy Savings Takaful",
          "Long-term savings combined with protection and Hibah (gift) planning.", 300.0));

  public static Optional<Plan> find(String id) {
    return PLANS.stream().filter(p -> p.id().equals(id)).findFirst();
  }

  private TakafulPlanCatalog() {}
}
