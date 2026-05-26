package com.microapp.onboarding.application;

import static akka.Done.done;
import static com.microapp.onboarding.domain.TakafulOnboarding.TakafulStage.ACTIVATING_POLICY;
import static com.microapp.onboarding.domain.TakafulOnboarding.TakafulStage.AWAITING_CONTRIBUTION;
import static com.microapp.onboarding.domain.TakafulOnboarding.TakafulStage.AWAITING_PLAN_SELECTION;
import static com.microapp.onboarding.domain.TakafulOnboarding.TakafulStage.COMPLETED;
import static com.microapp.onboarding.domain.TakafulOnboarding.TakafulStage.ISSUING_CERTIFICATE;
import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.workflow.Workflow;
import com.microapp.onboarding.domain.TakafulOnboarding;
import com.microapp.onboarding.domain.TakafulPlanCatalog;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resumable Takaful (Islamic insurance) onboarding — a deliberately distinct flow
 * from {@link CasaOnboardingWorkflow}: the user picks a plan, then sets a contribution;
 * the system screens eligibility, activates a policy, and issues a certificate.
 *
 * Like CASA it pauses for user input twice (plan, then contribution) and resumes
 * durably via {@link #submitStep}; unlike CASA the terminal artifact is a policy
 * number + certificate, not an account.
 */
@Component(id = "takaful-onboarding")
public class TakafulOnboardingWorkflow extends Workflow<TakafulOnboarding> {

  private static final Logger log = LoggerFactory.getLogger(TakafulOnboardingWorkflow.class);

  public record StartTakaful(String customerId) {}

  /** Generic submit carrying the field(s) for whichever stage is currently awaiting input. */
  public record SubmitTakafulStep(String planId, TakafulOnboarding.PlanContribution contribution) {}

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofSeconds(10))
        .defaultStepRecovery(RecoverStrategy.maxRetries(2).failoverTo(TakafulOnboardingWorkflow::failStep))
        .build();
  }

  // ---- command handlers ----

  public Effect<Done> start(StartTakaful cmd) {
    if (currentState() != null) {
      return effects().error("onboarding already started");
    }
    if (cmd.customerId() == null || cmd.customerId().isBlank()) {
      return effects().error("customerId is required");
    }
    return effects()
        .updateState(TakafulOnboarding.empty(commandContext().workflowId(), cmd.customerId()))
        .transitionTo(TakafulOnboardingWorkflow::selectPlanStep)
        .thenReply(done());
  }

  public Effect<Done> submitStep(SubmitTakafulStep cmd) {
    if (currentState() == null) {
      return effects().error("onboarding not started");
    }
    var stage = currentState().stage();
    return switch (stage) {
      case AWAITING_PLAN_SELECTION -> {
        if (cmd.planId() == null || TakafulPlanCatalog.find(cmd.planId()).isEmpty()) {
          yield effects().error("a valid takaful plan must be selected");
        }
        yield effects()
            .updateState(currentState().withPlan(cmd.planId()))
            .transitionTo(TakafulOnboardingWorkflow::collectContributionStep)
            .thenReply(done());
      }
      case AWAITING_CONTRIBUTION -> {
        if (cmd.contribution() == null || !cmd.contribution().isValid()) {
          yield effects().error("a valid contribution is required");
        }
        yield effects()
            .updateState(currentState().withContribution(cmd.contribution()))
            .transitionTo(TakafulOnboardingWorkflow::checkEligibilityStep)
            .thenReply(done());
      }
      default -> effects().error("cannot submit step while in stage " + stage);
    };
  }

  public ReadOnlyEffect<TakafulOnboarding> getStatus() {
    if (currentState() == null) {
      return effects().error("onboarding not started");
    }
    return effects().reply(currentState());
  }

  // ---- steps ----

  @StepName("select-plan")
  private StepEffect selectPlanStep() {
    return stepEffects().updateState(currentState().withStage(AWAITING_PLAN_SELECTION)).thenPause();
  }

  @StepName("collect-contribution")
  private StepEffect collectContributionStep() {
    return stepEffects().updateState(currentState().withStage(AWAITING_CONTRIBUTION)).thenPause();
  }

  @StepName("check-eligibility")
  private StepEffect checkEligibilityStep() {
    var s = currentState();
    var plan = TakafulPlanCatalog.find(s.selectedPlanId());
    boolean eligible =
        plan.isPresent()
            && s.contribution() != null
            && s.contribution().amount() >= plan.get().minMonthlyContribution();
    if (eligible) {
      return stepEffects().updateState(s.withStage(ACTIVATING_POLICY)).thenTransitionTo(TakafulOnboardingWorkflow::activatePolicyStep);
    }
    var min = plan.map(TakafulPlanCatalog.Plan::minMonthlyContribution).orElse(0.0);
    log.info("Takaful eligibility failed for application {}", s.applicationId());
    return stepEffects()
        .updateState(s.failed("Contribution is below the plan minimum of " + min))
        .thenEnd();
  }

  @StepName("activate-policy")
  private StepEffect activatePolicyStep() {
    var policyNumber = "TKF-" + Integer.toHexString(commandContext().workflowId().hashCode()).toUpperCase();
    var effectiveDate = LocalDate.now().toString();
    return stepEffects()
        .updateState(currentState().withPolicy(policyNumber, effectiveDate).withStage(ISSUING_CERTIFICATE))
        .thenTransitionTo(TakafulOnboardingWorkflow::issueCertificateStep);
  }

  @StepName("issue-certificate")
  private StepEffect issueCertificateStep() {
    var s = currentState();
    var planName = TakafulPlanCatalog.find(s.selectedPlanId()).map(TakafulPlanCatalog.Plan::name).orElse(s.selectedPlanId());
    var c = s.contribution();
    var summary =
        "Takaful certificate " + s.policyNumber() + " — " + planName
            + ", " + c.amount() + " " + c.frequency()
            + ", beneficiary " + c.beneficiaryName()
            + ", effective " + s.effectiveDate() + ".";
    return stepEffects().updateState(s.withCertificate(summary).withStage(COMPLETED)).thenEnd();
  }

  @StepName("fail")
  private StepEffect failStep() {
    return stepEffects().updateState(currentState().failed("onboarding failed during processing")).thenEnd();
  }
}
