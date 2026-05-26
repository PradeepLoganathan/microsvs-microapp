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
import com.microapp.onboarding.application.TakafulOnboardingWorkflow;
import com.microapp.onboarding.application.TakafulOnboardingWorkflow.StartTakaful;
import com.microapp.onboarding.application.TakafulOnboardingWorkflow.SubmitTakafulStep;
import com.microapp.onboarding.domain.CasaOnboarding;
import com.microapp.onboarding.domain.TakafulOnboarding;
import com.microapp.onboarding.domain.TakafulOnboarding.PlanContribution;
import com.microapp.onboarding.domain.TakafulPlanCatalog;
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
}
