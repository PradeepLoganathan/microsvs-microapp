package com.microapp.onboarding.application;

import static com.microapp.onboarding.domain.TakafulOnboarding.TakafulStage.AWAITING_CONTRIBUTION;
import static com.microapp.onboarding.domain.TakafulOnboarding.TakafulStage.AWAITING_PLAN_SELECTION;
import static com.microapp.onboarding.domain.TakafulOnboarding.TakafulStage.COMPLETED;
import static com.microapp.onboarding.domain.TakafulOnboarding.TakafulStage.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.testkit.TestKitSupport;
import com.microapp.onboarding.application.TakafulOnboardingWorkflow.StartTakaful;
import com.microapp.onboarding.application.TakafulOnboardingWorkflow.SubmitTakafulStep;
import com.microapp.onboarding.domain.TakafulOnboarding;
import com.microapp.onboarding.domain.TakafulOnboarding.PlanContribution;
import com.microapp.onboarding.domain.TakafulOnboarding.TakafulStage;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class TakafulOnboardingWorkflowTest extends TestKitSupport {

  // family-protect minimum contribution is 150.0
  private static final PlanContribution VALID_CONTRIBUTION =
      new PlanContribution(200.0, "MONTHLY", "Aisha Aziz");

  private TakafulOnboarding status(String id) {
    return componentClient.forWorkflow(id).method(TakafulOnboardingWorkflow::getStatus).invoke();
  }

  private void start(String id, String customerId) {
    componentClient.forWorkflow(id).method(TakafulOnboardingWorkflow::start).invoke(new StartTakaful(customerId));
  }

  private void submit(String id, SubmitTakafulStep step) {
    componentClient.forWorkflow(id).method(TakafulOnboardingWorkflow::submitStep).invoke(step);
  }

  private void awaitStage(String id, TakafulStage expected) {
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(status(id).stage()).isEqualTo(expected));
  }

  /** Distinct-flow resume: start → plan → (parked) → resume contribution → policy + certificate. */
  @Test
  public void resumeScenarioCompletes() {
    var appId = "takaful-" + UUID.randomUUID();

    start(appId, "acc-1001");
    awaitStage(appId, AWAITING_PLAN_SELECTION);

    submit(appId, new SubmitTakafulStep("family-protect", null));
    awaitStage(appId, AWAITING_CONTRIBUTION); // <-- durably parked at step 2

    var parked = status(appId);
    assertThat(parked.selectedPlanId()).isEqualTo("family-protect");
    assertThat(parked.policyNumber()).isNull();

    submit(appId, new SubmitTakafulStep(null, VALID_CONTRIBUTION));

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .ignoreExceptions()
        .untilAsserted(() -> {
          var s = status(appId);
          assertThat(s.stage()).isEqualTo(COMPLETED);
          assertThat(s.policyNumber()).startsWith("TKF-");
          assertThat(s.certificateSummary()).contains("family-protect".equals(s.selectedPlanId()) ? "Family Protect" : s.selectedPlanId());
          assertThat(s.effectiveDate()).isNotNull();
        });
  }

  @Test
  public void startTwiceRejected() {
    var appId = "takaful-" + UUID.randomUUID();
    start(appId, "acc-1001");
    awaitStage(appId, AWAITING_PLAN_SELECTION);
    assertThatThrownBy(() -> start(appId, "acc-1001")).hasMessageContaining("already started");
  }

  @Test
  public void unknownPlanRejected() {
    var appId = "takaful-" + UUID.randomUUID();
    start(appId, "acc-1001");
    awaitStage(appId, AWAITING_PLAN_SELECTION);
    assertThatThrownBy(() -> submit(appId, new SubmitTakafulStep("no-such-plan", null)))
        .hasMessageContaining("valid takaful plan");
  }

  @Test
  public void eligibilityFailsWhenContributionBelowMinimum() {
    var appId = "takaful-" + UUID.randomUUID();
    start(appId, "acc-1001");
    awaitStage(appId, AWAITING_PLAN_SELECTION);
    submit(appId, new SubmitTakafulStep("family-protect", null));
    awaitStage(appId, AWAITING_CONTRIBUTION);

    // 100 < family-protect minimum of 150 -> eligibility stub fails the application
    submit(appId, new SubmitTakafulStep(null, new PlanContribution(100.0, "MONTHLY", "Aisha Aziz")));

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .ignoreExceptions()
        .untilAsserted(() -> {
          var s = status(appId);
          assertThat(s.stage()).isEqualTo(FAILED);
          assertThat(s.failureReason()).containsIgnoringCase("minimum");
        });
  }
}
