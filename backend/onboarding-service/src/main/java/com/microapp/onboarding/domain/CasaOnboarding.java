package com.microapp.onboarding.domain;

/**
 * Durable state of a CASA (current/savings) account onboarding application.
 * Pure domain — no Akka dependencies. Mutations return new instances.
 *
 * Stage flow: STARTED → AWAITING_DETAILS (pause) → AWAITING_EKYC_CONSENT (pause)
 *   → RUNNING_EKYC → CREATING_ACCOUNT → WELCOME → COMPLETED   (or FAILED).
 */
public record CasaOnboarding(
    String applicationId,
    String customerId,
    CasaStage stage,
    Details details,
    EkycConsent ekyc,
    String accountId,
    String welcomeMessage,
    String failureReason) {

  public enum CasaStage {
    STARTED,
    AWAITING_DETAILS,
    AWAITING_EKYC_CONSENT,
    RUNNING_EKYC,
    CREATING_ACCOUNT,
    WELCOME,
    COMPLETED,
    FAILED
  }

  /** Personal/contact details collected in the first user-input stage. */
  public record Details(String fullName, String email, String mobile, String accountType) {
    public boolean isValid() {
      return notBlank(fullName) && notBlank(email) && notBlank(mobile) && notBlank(accountType);
    }
  }

  /** eKYC identity + consent collected in the second user-input stage. */
  public record EkycConsent(String idType, String idNumber, boolean consentGiven) {
    /** Stubbed verification: passes when consent is given and an id number is present. */
    public boolean passes() {
      return consentGiven && notBlank(idNumber);
    }
  }

  public static CasaOnboarding empty(String applicationId, String customerId) {
    return new CasaOnboarding(applicationId, customerId, CasaStage.STARTED, null, null, null, null, null);
  }

  public CasaOnboarding withStage(CasaStage newStage) {
    return new CasaOnboarding(applicationId, customerId, newStage, details, ekyc, accountId, welcomeMessage, failureReason);
  }

  public CasaOnboarding withDetails(Details newDetails) {
    return new CasaOnboarding(applicationId, customerId, stage, newDetails, ekyc, accountId, welcomeMessage, failureReason);
  }

  public CasaOnboarding withEkyc(EkycConsent newEkyc) {
    return new CasaOnboarding(applicationId, customerId, stage, details, newEkyc, accountId, welcomeMessage, failureReason);
  }

  public CasaOnboarding withAccount(String newAccountId) {
    return new CasaOnboarding(applicationId, customerId, stage, details, ekyc, newAccountId, welcomeMessage, failureReason);
  }

  public CasaOnboarding withWelcome(String message) {
    return new CasaOnboarding(applicationId, customerId, stage, details, ekyc, accountId, message, failureReason);
  }

  public CasaOnboarding failed(String reason) {
    return new CasaOnboarding(applicationId, customerId, CasaStage.FAILED, details, ekyc, accountId, welcomeMessage, reason);
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
