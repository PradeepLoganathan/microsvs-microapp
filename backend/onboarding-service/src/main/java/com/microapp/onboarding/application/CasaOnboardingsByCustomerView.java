package com.microapp.onboarding.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.microapp.onboarding.domain.CasaOnboarding;
import java.util.Collection;

/**
 * Projects in-flight and completed CASA onboardings by customerId, so the
 * home page can list "your accounts". Eventually consistent — the workflow
 * is the source of truth.
 */
@Component(id = "casa-onboardings-by-customer")
public class CasaOnboardingsByCustomerView extends View {

  public record CasaRow(
      String applicationId,
      String customerId,
      String stage,
      String accountId,
      String fullName,
      String accountType,
      long updatedAt) {}

  public record CasaApplications(Collection<CasaRow> casaApplications) {}

  @Consume.FromWorkflow(CasaOnboardingWorkflow.class)
  public static class CasaRowUpdater extends TableUpdater<CasaRow> {

    public Effect<CasaRow> onUpdate(CasaOnboarding state) {
      // View columns are non-optional Strings — never store null, use "" placeholders.
      var details = state.details();
      return effects().updateRow(new CasaRow(
          nz(state.applicationId()),
          nz(state.customerId()),
          state.stage() == null ? "" : state.stage().name(),
          nz(state.accountId()),
          details == null ? "" : nz(details.fullName()),
          details == null ? "" : nz(details.accountType()),
          System.currentTimeMillis()));
    }

    private static String nz(String s) {
      return s == null ? "" : s;
    }
  }

  @Query("SELECT * AS casaApplications FROM casa_onboardings_by_customer WHERE customerId = :customerId")
  public QueryEffect<CasaApplications> byCustomer(String customerId) {
    return queryResult();
  }
}
