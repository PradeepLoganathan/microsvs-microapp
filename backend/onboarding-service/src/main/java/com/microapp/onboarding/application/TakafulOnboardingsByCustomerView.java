package com.microapp.onboarding.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.microapp.onboarding.domain.TakafulOnboarding;
import java.util.Collection;

/**
 * Projects in-flight and completed Takaful onboardings by customerId. Distinct
 * from CASA: a takaful application terminates in a policy, not an account.
 */
@Component(id = "takaful-onboardings-by-customer")
public class TakafulOnboardingsByCustomerView extends View {

  public record TakafulRow(
      String applicationId,
      String customerId,
      String stage,
      String selectedPlanId,
      String policyNumber,
      String effectiveDate,
      double contributionAmount,
      String contributionFrequency,
      long updatedAt) {}

  public record TakafulApplications(Collection<TakafulRow> takafulApplications) {}

  @Consume.FromWorkflow(TakafulOnboardingWorkflow.class)
  public static class TakafulRowUpdater extends TableUpdater<TakafulRow> {

    public Effect<TakafulRow> onUpdate(TakafulOnboarding state) {
      var c = state.contribution();
      return effects().updateRow(new TakafulRow(
          nz(state.applicationId()),
          nz(state.customerId()),
          state.stage() == null ? "" : state.stage().name(),
          nz(state.selectedPlanId()),
          nz(state.policyNumber()),
          nz(state.effectiveDate()),
          c == null ? 0.0 : c.amount(),
          c == null ? "" : nz(c.frequency()),
          System.currentTimeMillis()));
    }

    private static String nz(String s) {
      return s == null ? "" : s;
    }
  }

  @Query("SELECT * AS takafulApplications FROM takaful_onboardings_by_customer WHERE customerId = :customerId")
  public QueryEffect<TakafulApplications> byCustomer(String customerId) {
    return queryResult();
  }
}
