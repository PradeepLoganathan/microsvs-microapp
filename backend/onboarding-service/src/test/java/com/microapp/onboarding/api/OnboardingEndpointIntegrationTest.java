package com.microapp.onboarding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.testkit.TestKitSupport;
import com.microapp.onboarding.api.OnboardingEndpoint.CasaStatusView;
import com.microapp.onboarding.api.OnboardingEndpoint.ContributionBody;
import com.microapp.onboarding.api.OnboardingEndpoint.DetailsBody;
import com.microapp.onboarding.api.OnboardingEndpoint.EkycBody;
import com.microapp.onboarding.api.OnboardingEndpoint.StartRequest;
import com.microapp.onboarding.api.OnboardingEndpoint.SubmitCasaRequest;
import com.microapp.onboarding.api.OnboardingEndpoint.SubmitTakafulRequest;
import com.microapp.onboarding.api.OnboardingEndpoint.TakafulStatusView;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class OnboardingEndpointIntegrationTest extends TestKitSupport {

  private CasaStatusView casaStatus(String id) {
    return httpClient.GET("/onboarding/casa/" + id + "/status").responseBodyAs(CasaStatusView.class).invoke().body();
  }

  private TakafulStatusView takafulStatus(String id) {
    return httpClient.GET("/onboarding/takaful/" + id + "/status").responseBodyAs(TakafulStatusView.class).invoke().body();
  }

  private void awaitCasaStage(String id, String stage) {
    Awaitility.await().atMost(10, TimeUnit.SECONDS).ignoreExceptions()
        .untilAsserted(() -> assertThat(casaStatus(id).stage()).isEqualTo(stage));
  }

  private void awaitTakafulStage(String id, String stage) {
    Awaitility.await().atMost(10, TimeUnit.SECONDS).ignoreExceptions()
        .untilAsserted(() -> assertThat(takafulStatus(id).stage()).isEqualTo(stage));
  }

  /** RFI acceptance over HTTP: start → step 1 → status shows step 2 → step 2 → completed. */
  @Test
  public void casaResumeOverHttp() {
    var id = "casa-http-" + UUID.randomUUID();

    var start = httpClient.POST("/onboarding/casa/" + id + "/start")
        .withRequestBody(new StartRequest("acc-1001"))
        .responseBodyAs(CasaStatusView.class).invoke();
    assertThat(start.status().isSuccess()).isTrue();
    awaitCasaStage(id, "AWAITING_DETAILS");

    httpClient.POST("/onboarding/casa/" + id + "/submit")
        .withRequestBody(new SubmitCasaRequest(
            new DetailsBody("Sara Aziz", "sara@example.my", "0123456789", "SAVINGS"), null))
        .responseBodyAs(CasaStatusView.class).invoke();

    // "app closed": GET status shows it parked at step 2
    awaitCasaStage(id, "AWAITING_EKYC_CONSENT");

    // resume
    httpClient.POST("/onboarding/casa/" + id + "/submit")
        .withRequestBody(new SubmitCasaRequest(null, new EkycBody("NRIC", "900101-01-1234", true)))
        .responseBodyAs(CasaStatusView.class).invoke();

    Awaitility.await().atMost(10, TimeUnit.SECONDS).ignoreExceptions().untilAsserted(() -> {
      var s = casaStatus(id);
      assertThat(s.stage()).isEqualTo("COMPLETED");
      assertThat(s.accountId()).startsWith("CASA-");
    });
  }

  @Test
  public void takafulDistinctFlowOverHttp() {
    var id = "takaful-http-" + UUID.randomUUID();

    httpClient.POST("/onboarding/takaful/" + id + "/start")
        .withRequestBody(new StartRequest("acc-1001"))
        .responseBodyAs(TakafulStatusView.class).invoke();
    awaitTakafulStage(id, "AWAITING_PLAN_SELECTION");

    httpClient.POST("/onboarding/takaful/" + id + "/submit")
        .withRequestBody(new SubmitTakafulRequest("family-protect", null))
        .responseBodyAs(TakafulStatusView.class).invoke();
    awaitTakafulStage(id, "AWAITING_CONTRIBUTION");

    httpClient.POST("/onboarding/takaful/" + id + "/submit")
        .withRequestBody(new SubmitTakafulRequest(null, new ContributionBody(200.0, "MONTHLY", "Aisha Aziz")))
        .responseBodyAs(TakafulStatusView.class).invoke();

    Awaitility.await().atMost(10, TimeUnit.SECONDS).ignoreExceptions().untilAsserted(() -> {
      var s = takafulStatus(id);
      assertThat(s.stage()).isEqualTo("COMPLETED");
      assertThat(s.policyNumber()).startsWith("TKF-");
      assertThat(s.effectiveDate()).isNotBlank();
    });
  }

  @Test
  public void statusBeforeStartIsError() {
    var id = "casa-missing-" + UUID.randomUUID();
    assertThatThrownBy(() -> casaStatus(id));
  }
}
