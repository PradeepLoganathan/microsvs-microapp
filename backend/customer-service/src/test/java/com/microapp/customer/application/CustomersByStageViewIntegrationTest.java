package com.microapp.customer.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import com.microapp.customer.application.CustomerEntity.EkycRequest;
import com.microapp.customer.application.CustomersByStageView.CustomerRow;
import com.microapp.customer.domain.Customer.Registration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class CustomersByStageViewIntegrationTest extends TestKitSupport {

  private Collection<CustomerRow> byStage(String stage) {
    return componentClient.forView().method(CustomersByStageView::byStage).invoke(stage).customers();
  }

  @Test
  public void rowMovesThroughStages() {
    var id = "visitor-" + UUID.randomUUID();

    componentClient.forEventSourcedEntity(id).method(CustomerEntity::createVisitor).invoke("REFERRAL");
    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
        assertThat(byStage("VISITOR")).anyMatch(r -> r.customerId().equals(id)));

    componentClient.forEventSourcedEntity(id).method(CustomerEntity::register)
        .invoke(new Registration("sara@example.my", "0123456789", "REFERRAL"));
    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      var row = byStage("REGISTERED").stream().filter(r -> r.customerId().equals(id)).findFirst();
      assertThat(row).isPresent();
      assertThat(row.get().offerCode()).isEqualTo("WELCOME-REF50"); // REFERRAL channel rule
      assertThat(row.get().accountId()).isEqualTo("acc-1001");
    });

    componentClient.forEventSourcedEntity(id).method(CustomerEntity::completeKyc)
        .invoke(new EkycRequest("NRIC", "900101-01-1234", true));
    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
        assertThat(byStage("CUSTOMER")).anyMatch(r -> r.customerId().equals(id)));
  }
}
