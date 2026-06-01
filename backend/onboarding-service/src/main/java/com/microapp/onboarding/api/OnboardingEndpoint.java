package com.microapp.onboarding.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.microapp.onboarding.application.CasaOnboardingWorkflow;
import com.microapp.onboarding.application.CasaOnboardingWorkflow.StartCasa;
import com.microapp.onboarding.application.CasaOnboardingWorkflow.SubmitCasaStep;
import com.microapp.onboarding.application.CasaOnboardingsByCustomerView;
import com.microapp.onboarding.application.CasaOnboardingsByCustomerView.CasaRow;
import com.microapp.onboarding.application.TakafulOnboardingWorkflow;
import com.microapp.onboarding.application.TakafulOnboardingWorkflow.StartTakaful;
import com.microapp.onboarding.application.TakafulOnboardingWorkflow.SubmitTakafulStep;
import com.microapp.onboarding.application.TakafulOnboardingsByCustomerView;
import com.microapp.onboarding.application.TakafulOnboardingsByCustomerView.TakafulRow;
import com.microapp.onboarding.domain.CasaOnboarding;
import com.microapp.onboarding.domain.TakafulOnboarding;
import com.microapp.onboarding.domain.TakafulOnboarding.PlanContribution;
import com.microapp.onboarding.domain.TakafulPlanCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * HTTP API for resumable CASA and Takaful onboarding. {@code applicationId} (path) is
 * the workflow id — the client generates and keeps it so reopening + GET status + submit
 * resumes the same application. Returns API status views (never the workflow/domain types).
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/onboarding")
public class OnboardingEndpoint extends AbstractHttpEndpoint {

  private final ComponentClient componentClient;

  public OnboardingEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // ---- request / response records ----

  public record StartRequest(String customerId) {}

  public record DetailsBody(String fullName, String email, String mobile, String accountType) {}

  public record EkycBody(String idType, String idNumber, boolean consentGiven) {}

  public record SubmitCasaRequest(DetailsBody details, EkycBody ekyc) {}

  public record ContributionBody(double amount, String frequency, String beneficiaryName) {}

  public record SubmitTakafulRequest(String planId, ContributionBody contribution) {}

  public record CasaStatusView(
      String applicationId, String customerId, String stage,
      String accountId, String welcomeMessage, String failureReason) {}

  public record TakafulStatusView(
      String applicationId, String customerId, String stage, String selectedPlanId,
      String policyNumber, String effectiveDate, String certificateSummary, String failureReason) {}

  public record PlanView(String id, String name, String description, double minMonthlyContribution) {}

  /** Unified shape across CASA + Takaful for the home page's "Your accounts" card. */
  public record AccountView(
      String applicationId,
      String product,        // "CASA" or "TAKAFUL"
      String productName,    // friendly label, e.g. "Savings Account" or "Family Takaful"
      String stage,          // raw workflow stage
      String statusLabel,    // human-friendly status ("Pending eKYC", "Ready to use", ...)
      String accountNumber,  // accountId for CASA, policyNumber for Takaful; "" if not yet assigned
      String summary,        // "RM 0 opening balance" / "RM 150 / MONTHLY" / "Awaiting plan"
      long updatedAt) {}

  public record Applications(List<AccountView> applications) {}

  // ---- CASA ----

  @Post("/casa/{applicationId}/start")
  public CasaStatusView startCasa(String applicationId, StartRequest req) {
    componentClient.forWorkflow(applicationId)
        .method(CasaOnboardingWorkflow::start)
        .invoke(new StartCasa(req.customerId()));
    return casaStatus(applicationId);
  }

  @Post("/casa/{applicationId}/submit")
  public CasaStatusView submitCasa(String applicationId, SubmitCasaRequest req) {
    var details = req.details() == null ? null
        : new CasaOnboarding.Details(req.details().fullName(), req.details().email(),
            req.details().mobile(), req.details().accountType());
    var ekyc = req.ekyc() == null ? null
        : new CasaOnboarding.EkycConsent(req.ekyc().idType(), req.ekyc().idNumber(), req.ekyc().consentGiven());
    componentClient.forWorkflow(applicationId)
        .method(CasaOnboardingWorkflow::submitStep)
        .invoke(new SubmitCasaStep(details, ekyc));
    return casaStatus(applicationId);
  }

  @Get("/casa/{applicationId}/status")
  public CasaStatusView casaStatus(String applicationId) {
    var s = componentClient.forWorkflow(applicationId).method(CasaOnboardingWorkflow::getStatus).invoke();
    return new CasaStatusView(s.applicationId(), s.customerId(), s.stage().name(),
        s.accountId(), s.welcomeMessage(), s.failureReason());
  }

  // ---- Takaful ----

  @Post("/takaful/{applicationId}/start")
  public TakafulStatusView startTakaful(String applicationId, StartRequest req) {
    componentClient.forWorkflow(applicationId)
        .method(TakafulOnboardingWorkflow::start)
        .invoke(new StartTakaful(req.customerId()));
    return takafulStatus(applicationId);
  }

  @Post("/takaful/{applicationId}/submit")
  public TakafulStatusView submitTakaful(String applicationId, SubmitTakafulRequest req) {
    var contribution = req.contribution() == null ? null
        : new PlanContribution(req.contribution().amount(), req.contribution().frequency(),
            req.contribution().beneficiaryName());
    componentClient.forWorkflow(applicationId)
        .method(TakafulOnboardingWorkflow::submitStep)
        .invoke(new SubmitTakafulStep(req.planId(), contribution));
    return takafulStatus(applicationId);
  }

  @Get("/takaful/{applicationId}/status")
  public TakafulStatusView takafulStatus(String applicationId) {
    var s = componentClient.forWorkflow(applicationId).method(TakafulOnboardingWorkflow::getStatus).invoke();
    return new TakafulStatusView(s.applicationId(), s.customerId(), s.stage().name(), s.selectedPlanId(),
        s.policyNumber(), s.effectiveDate(), s.certificateSummary(), s.failureReason());
  }

  @Get("/takaful/plans")
  public List<PlanView> takafulPlans() {
    return TakafulPlanCatalog.PLANS.stream()
        .map(p -> new PlanView(p.id(), p.name(), p.description(), p.minMonthlyContribution()))
        .toList();
  }

  // ---- Combined: accounts owned by a customer (CASA + Takaful, newest first) ----

  @Get("/customers/{customerId}/applications")
  public Applications applicationsByCustomer(String customerId) {
    var casa = componentClient.forView()
        .method(CasaOnboardingsByCustomerView::byCustomer)
        .invoke(customerId)
        .casaApplications();
    var takaful = componentClient.forView()
        .method(TakafulOnboardingsByCustomerView::byCustomer)
        .invoke(customerId)
        .takafulApplications();

    var combined = new ArrayList<AccountView>();
    for (var r : casa) combined.add(toAccountView(r));
    for (var r : takaful) combined.add(toAccountView(r));
    combined.sort(Comparator.comparingLong(AccountView::updatedAt).reversed());
    return new Applications(combined);
  }

  private static AccountView toAccountView(CasaRow r) {
    var productName = r.accountType().isBlank() ? "CASA Account" : r.accountType() + " Account";
    return new AccountView(
        r.applicationId(),
        "CASA",
        productName,
        r.stage(),
        casaStatusLabel(r.stage()),
        r.accountId(),
        "RM 0 opening balance",
        r.updatedAt());
  }

  private static AccountView toAccountView(TakafulRow r) {
    var productName = TakafulPlanCatalog.PLANS.stream()
        .filter(p -> p.id().equals(r.selectedPlanId()))
        .findFirst()
        .map(p -> p.name())
        .orElse("Family Takaful");
    var summary = r.contributionAmount() > 0
        ? String.format("RM %.0f / %s", r.contributionAmount(),
            r.contributionFrequency().isBlank() ? "month" : r.contributionFrequency().toLowerCase())
        : "Awaiting plan & contribution";
    return new AccountView(
        r.applicationId(),
        "TAKAFUL",
        productName,
        r.stage(),
        takafulStatusLabel(r.stage()),
        r.policyNumber(),
        summary,
        r.updatedAt());
  }

  private static String casaStatusLabel(String stage) {
    return switch (stage) {
      case "STARTED", "AWAITING_DETAILS" -> "Pending your details";
      case "AWAITING_EKYC_CONSENT" -> "Pending eKYC";
      case "RUNNING_EKYC", "CREATING_ACCOUNT", "WELCOME" -> "Setting up";
      case "COMPLETED" -> "Ready to use";
      case "FAILED" -> "Application failed";
      default -> stage;
    };
  }

  private static String takafulStatusLabel(String stage) {
    return switch (stage) {
      case "STARTED", "AWAITING_PLAN_SELECTION" -> "Choose a plan";
      case "AWAITING_CONTRIBUTION" -> "Set contribution";
      case "CHECKING_ELIGIBILITY", "ACTIVATING_POLICY", "ISSUING_CERTIFICATE" -> "Setting up";
      case "COMPLETED" -> "Active";
      case "FAILED" -> "Application failed";
      default -> stage;
    };
  }
}
