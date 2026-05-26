package com.microapp.onboarding.application;

import static akka.Done.done;
import static com.microapp.onboarding.domain.CasaOnboarding.CasaStage.AWAITING_DETAILS;
import static com.microapp.onboarding.domain.CasaOnboarding.CasaStage.AWAITING_EKYC_CONSENT;
import static com.microapp.onboarding.domain.CasaOnboarding.CasaStage.COMPLETED;
import static com.microapp.onboarding.domain.CasaOnboarding.CasaStage.CREATING_ACCOUNT;
import static com.microapp.onboarding.domain.CasaOnboarding.CasaStage.RUNNING_EKYC;
import static com.microapp.onboarding.domain.CasaOnboarding.CasaStage.WELCOME;
import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.workflow.Workflow;
import com.microapp.onboarding.domain.CasaOnboarding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resumable CASA (current/savings) account onboarding.
 *
 * Two user-input stages pause the workflow durably (collect details, then eKYC
 * consent); the user resumes by calling {@link #submitStep} with the data for the
 * current stage. After eKYC the remaining steps run automatically. The workflow
 * state survives the client closing the app, so {@link #getStatus} + a later
 * {@code submitStep} resume exactly where it left off.
 */
@Component(id = "casa-onboarding")
public class CasaOnboardingWorkflow extends Workflow<CasaOnboarding> {

  private static final Logger log = LoggerFactory.getLogger(CasaOnboardingWorkflow.class);

  public record StartCasa(String customerId) {}

  /** Generic submit carrying the field(s) for whichever stage is currently awaiting input. */
  public record SubmitCasaStep(CasaOnboarding.Details details, CasaOnboarding.EkycConsent ekyc) {}

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofSeconds(10))
        .defaultStepRecovery(RecoverStrategy.maxRetries(2).failoverTo(CasaOnboardingWorkflow::failStep))
        .build();
    // NOTE: deliberately no global workflow timeout — a paused (abandoned) application
    // must be resumable whenever the customer returns.
  }

  // ---- command handlers (Effect API) ----

  public Effect<Done> start(StartCasa cmd) {
    if (currentState() != null) {
      return effects().error("onboarding already started");
    }
    if (cmd.customerId() == null || cmd.customerId().isBlank()) {
      return effects().error("customerId is required");
    }
    return effects()
        .updateState(CasaOnboarding.empty(commandContext().workflowId(), cmd.customerId()))
        .transitionTo(CasaOnboardingWorkflow::collectDetailsStep)
        .thenReply(done());
  }

  public Effect<Done> submitStep(SubmitCasaStep cmd) {
    if (currentState() == null) {
      return effects().error("onboarding not started");
    }
    var stage = currentState().stage();
    return switch (stage) {
      case AWAITING_DETAILS -> {
        if (cmd.details() == null || !cmd.details().isValid()) {
          yield effects().error("valid applicant details are required");
        }
        yield effects()
            .updateState(currentState().withDetails(cmd.details()))
            .transitionTo(CasaOnboardingWorkflow::requestEkycStep)
            .thenReply(done());
      }
      case AWAITING_EKYC_CONSENT -> {
        if (cmd.ekyc() == null) {
          yield effects().error("eKYC consent is required");
        }
        yield effects()
            .updateState(currentState().withEkyc(cmd.ekyc()))
            .transitionTo(CasaOnboardingWorkflow::runEkycStep)
            .thenReply(done());
      }
      default -> effects().error("cannot submit step while in stage " + stage);
    };
  }

  public ReadOnlyEffect<CasaOnboarding> getStatus() {
    if (currentState() == null) {
      return effects().error("onboarding not started");
    }
    return effects().reply(currentState());
  }

  // ---- steps (StepEffect API) ----

  @StepName("collect-details")
  private StepEffect collectDetailsStep() {
    // Pause #1: wait for the applicant to submit their details.
    return stepEffects().updateState(currentState().withStage(AWAITING_DETAILS)).thenPause();
  }

  @StepName("request-ekyc")
  private StepEffect requestEkycStep() {
    // Pause #2: wait for eKYC identity + consent.
    return stepEffects().updateState(currentState().withStage(AWAITING_EKYC_CONSENT)).thenPause();
  }

  @StepName("run-ekyc")
  private StepEffect runEkycStep() {
    var ekyc = currentState().ekyc();
    if (ekyc != null && ekyc.passes()) {
      return stepEffects()
          .updateState(currentState().withStage(CREATING_ACCOUNT))
          .thenTransitionTo(CasaOnboardingWorkflow::createAccountStep);
    }
    log.info("eKYC failed for application {}", currentState().applicationId());
    return stepEffects()
        .updateState(currentState().failed("eKYC could not be verified"))
        .thenEnd();
  }

  @StepName("create-account")
  private StepEffect createAccountStep() {
    // Deterministic id from the (stable) workflow id, so a retried step is idempotent.
    var accountId = "CASA-" + Integer.toHexString(commandContext().workflowId().hashCode()).toUpperCase();
    return stepEffects()
        .updateState(currentState().withAccount(accountId).withStage(WELCOME))
        .thenTransitionTo(CasaOnboardingWorkflow::welcomeStep);
  }

  @StepName("welcome")
  private StepEffect welcomeStep() {
    var s = currentState();
    var name = s.details() != null ? s.details().fullName() : "there";
    var type = s.details() != null ? s.details().accountType() : "CASA";
    var msg = "Welcome " + name + "! Your " + type + " account " + s.accountId() + " is now open.";
    return stepEffects().updateState(s.withWelcome(msg).withStage(COMPLETED)).thenEnd();
  }

  @StepName("fail")
  private StepEffect failStep() {
    return stepEffects().updateState(currentState().failed("onboarding failed during processing")).thenEnd();
  }
}
