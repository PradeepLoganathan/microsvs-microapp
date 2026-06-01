package com.microapp.onboarding.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import com.microapp.onboarding.application.CasaOnboardingWorkflow.StartCasa;
import com.microapp.onboarding.application.CasaOnboardingsByCustomerView.CasaRow;
import com.microapp.onboarding.application.TakafulOnboardingWorkflow.StartTakaful;
import com.microapp.onboarding.application.TakafulOnboardingsByCustomerView.TakafulRow;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class OnboardingsByCustomerViewIntegrationTest extends TestKitSupport {

  private Collection<CasaRow> casaByCustomer(String customerId) {
    return componentClient.forView()
        .method(CasaOnboardingsByCustomerView::byCustomer)
        .invoke(customerId)
        .casaApplications();
  }

  private Collection<TakafulRow> takafulByCustomer(String customerId) {
    return componentClient.forView()
        .method(TakafulOnboardingsByCustomerView::byCustomer)
        .invoke(customerId)
        .takafulApplications();
  }

  @Test
  public void casaWorkflowStartSurfacesInView() {
    var customerId = "acc-" + UUID.randomUUID();
    var applicationId = "casa-" + UUID.randomUUID();

    componentClient
        .forWorkflow(applicationId)
        .method(CasaOnboardingWorkflow::start)
        .invoke(new StartCasa(customerId));

    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      var row = casaByCustomer(customerId).stream()
          .filter(r -> r.applicationId().equals(applicationId)).findFirst();
      assertThat(row).isPresent();
      assertThat(row.get().customerId()).isEqualTo(customerId);
      assertThat(row.get().stage()).isNotBlank();
    });
  }

  @Test
  public void takafulWorkflowStartSurfacesInView() {
    var customerId = "acc-" + UUID.randomUUID();
    var applicationId = "takaful-" + UUID.randomUUID();

    componentClient
        .forWorkflow(applicationId)
        .method(TakafulOnboardingWorkflow::start)
        .invoke(new StartTakaful(customerId));

    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      var row = takafulByCustomer(customerId).stream()
          .filter(r -> r.applicationId().equals(applicationId)).findFirst();
      assertThat(row).isPresent();
      assertThat(row.get().customerId()).isEqualTo(customerId);
      assertThat(row.get().stage()).isNotBlank();
    });
  }

  @Test
  public void distinctCustomersDoNotCrossPollinate() {
    var alice = "acc-alice-" + UUID.randomUUID();
    var bob = "acc-bob-" + UUID.randomUUID();
    var aliceApp = "casa-" + UUID.randomUUID();
    var bobApp = "casa-" + UUID.randomUUID();

    componentClient.forWorkflow(aliceApp).method(CasaOnboardingWorkflow::start).invoke(new StartCasa(alice));
    componentClient.forWorkflow(bobApp).method(CasaOnboardingWorkflow::start).invoke(new StartCasa(bob));

    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(casaByCustomer(alice)).anyMatch(r -> r.applicationId().equals(aliceApp));
      assertThat(casaByCustomer(alice)).noneMatch(r -> r.applicationId().equals(bobApp));
      assertThat(casaByCustomer(bob)).anyMatch(r -> r.applicationId().equals(bobApp));
    });
  }
}
