package com.microapp.onboarding.application;

import static com.microapp.onboarding.domain.CasaOnboarding.CasaStage.AWAITING_DETAILS;
import static com.microapp.onboarding.domain.CasaOnboarding.CasaStage.AWAITING_EKYC_CONSENT;
import static com.microapp.onboarding.domain.CasaOnboarding.CasaStage.COMPLETED;
import static com.microapp.onboarding.domain.CasaOnboarding.CasaStage.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.testkit.TestKitSupport;
import com.microapp.onboarding.application.CasaOnboardingWorkflow.StartCasa;
import com.microapp.onboarding.application.CasaOnboardingWorkflow.SubmitCasaStep;
import com.microapp.onboarding.domain.CasaOnboarding;
import com.microapp.onboarding.domain.CasaOnboarding.CasaStage;
import com.microapp.onboarding.domain.CasaOnboarding.Details;
import com.microapp.onboarding.domain.CasaOnboarding.EkycConsent;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class CasaOnboardingWorkflowTest extends TestKitSupport {

  private static final Details VALID_DETAILS =
      new Details("Sara Aziz", "sara@example.my", "0123456789", "SAVINGS");

  private CasaOnboarding status(String id) {
    return componentClient.forWorkflow(id).method(CasaOnboardingWorkflow::getStatus).invoke();
  }

  private void start(String id, String customerId) {
    componentClient.forWorkflow(id).method(CasaOnboardingWorkflow::start).invoke(new StartCasa(customerId));
  }

  private void submit(String id, SubmitCasaStep step) {
    componentClient.forWorkflow(id).method(CasaOnboardingWorkflow::submitStep).invoke(step);
  }

  private void awaitStage(String id, CasaStage expected) {
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(status(id).stage()).isEqualTo(expected));
  }

  /** The acceptance "money-shot": start → step 1 → (parked) → resume step 2 → completed. */
  @Test
  public void resumeScenarioCompletes() {
    var appId = "casa-" + UUID.randomUUID();

    start(appId, "acc-1001");
    awaitStage(appId, AWAITING_DETAILS);

    submit(appId, new SubmitCasaStep(VALID_DETAILS, null));
    awaitStage(appId, AWAITING_EKYC_CONSENT); // <-- "app closed": durably parked at step 2

    // Re-read after the "reopen": details from step 1 are still there.
    var parked = status(appId);
    assertThat(parked.details().fullName()).isEqualTo("Sara Aziz");
    assertThat(parked.accountId()).isNull();

    // Resume by submitting step 2 (same workflow id).
    submit(appId, new SubmitCasaStep(null, new EkycConsent("NRIC", "900101-01-1234", true)));

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .ignoreExceptions()
        .untilAsserted(() -> {
          var s = status(appId);
          assertThat(s.stage()).isEqualTo(COMPLETED);
          assertThat(s.accountId()).startsWith("CASA-");
          assertThat(s.welcomeMessage()).contains("Sara Aziz");
        });
  }

  @Test
  public void startTwiceRejected() {
    var appId = "casa-" + UUID.randomUUID();
    start(appId, "acc-1001");
    awaitStage(appId, AWAITING_DETAILS);
    assertThatThrownBy(() -> start(appId, "acc-1001")).hasMessageContaining("already started");
  }

  @Test
  public void submitRejectedAfterCompletion() {
    var appId = "casa-" + UUID.randomUUID();
    start(appId, "acc-1001");
    awaitStage(appId, AWAITING_DETAILS);
    submit(appId, new SubmitCasaStep(VALID_DETAILS, null));
    awaitStage(appId, AWAITING_EKYC_CONSENT);
    submit(appId, new SubmitCasaStep(null, new EkycConsent("NRIC", "900101-01-1234", true)));
    awaitStage(appId, COMPLETED);

    assertThatThrownBy(() -> submit(appId, new SubmitCasaStep(VALID_DETAILS, null)))
        .hasMessageContaining("cannot submit step");
  }

  @Test
  public void ekycRejectionFails() {
    var appId = "casa-" + UUID.randomUUID();
    start(appId, "acc-1001");
    awaitStage(appId, AWAITING_DETAILS);
    submit(appId, new SubmitCasaStep(VALID_DETAILS, null));
    awaitStage(appId, AWAITING_EKYC_CONSENT);

    // consent not given -> eKYC stub fails the application
    submit(appId, new SubmitCasaStep(null, new EkycConsent("NRIC", "900101-01-1234", false)));

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .ignoreExceptions()
        .untilAsserted(() -> {
          var s = status(appId);
          assertThat(s.stage()).isEqualTo(FAILED);
          assertThat(s.failureReason()).containsIgnoringCase("eKYC");
        });
  }
}
