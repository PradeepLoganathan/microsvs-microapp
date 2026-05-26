package com.microapp.onboarding.domain;

/**
 * Durable state of a Takaful (Islamic insurance) onboarding application.
 * Pure domain — no Akka dependencies. Deliberately distinct from {@link CasaOnboarding}:
 * different stages, collected data, and terminal artifact (policy vs account).
 *
 * Stage flow: STARTED → AWAITING_PLAN_SELECTION (pause) → AWAITING_CONTRIBUTION (pause)
 *   → CHECKING_ELIGIBILITY → ACTIVATING_POLICY → ISSUING_CERTIFICATE → COMPLETED  (or FAILED).
 */
public record TakafulOnboarding(
    String applicationId,
    String customerId,
    TakafulStage stage,
    String selectedPlanId,
    PlanContribution contribution,
    String policyNumber,
    String effectiveDate,
    String certificateSummary,
    String failureReason) {

  public enum TakafulStage {
    STARTED,
    AWAITING_PLAN_SELECTION,
    AWAITING_CONTRIBUTION,
    CHECKING_ELIGIBILITY,
    ACTIVATING_POLICY,
    ISSUING_CERTIFICATE,
    COMPLETED,
    FAILED
  }

  /** Contribution chosen in the second user-input stage. */
  public record PlanContribution(double amount, String frequency, String beneficiaryName) {
    public boolean isValid() {
      return amount > 0
          && frequency != null && !frequency.isBlank()
          && beneficiaryName != null && !beneficiaryName.isBlank();
    }
  }

  public static TakafulOnboarding empty(String applicationId, String customerId) {
    return new TakafulOnboarding(
        applicationId, customerId, TakafulStage.STARTED, null, null, null, null, null, null);
  }

  public TakafulOnboarding withStage(TakafulStage newStage) {
    return new TakafulOnboarding(applicationId, customerId, newStage, selectedPlanId, contribution, policyNumber, effectiveDate, certificateSummary, failureReason);
  }

  public TakafulOnboarding withPlan(String planId) {
    return new TakafulOnboarding(applicationId, customerId, stage, planId, contribution, policyNumber, effectiveDate, certificateSummary, failureReason);
  }

  public TakafulOnboarding withContribution(PlanContribution c) {
    return new TakafulOnboarding(applicationId, customerId, stage, selectedPlanId, c, policyNumber, effectiveDate, certificateSummary, failureReason);
  }

  public TakafulOnboarding withPolicy(String policy, String effective) {
    return new TakafulOnboarding(applicationId, customerId, stage, selectedPlanId, contribution, policy, effective, certificateSummary, failureReason);
  }

  public TakafulOnboarding withCertificate(String summary) {
    return new TakafulOnboarding(applicationId, customerId, stage, selectedPlanId, contribution, policyNumber, effectiveDate, summary, failureReason);
  }

  public TakafulOnboarding failed(String reason) {
    return new TakafulOnboarding(applicationId, customerId, TakafulStage.FAILED, selectedPlanId, contribution, policyNumber, effectiveDate, certificateSummary, reason);
  }
}
