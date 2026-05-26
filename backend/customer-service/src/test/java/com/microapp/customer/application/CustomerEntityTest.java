package com.microapp.customer.application;

import static com.microapp.customer.domain.Customer.Stage.CUSTOMER;
import static com.microapp.customer.domain.Customer.Stage.REGISTERED;
import static com.microapp.customer.domain.Customer.Stage.VISITOR;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.microapp.customer.application.CustomerEntity.EkycRequest;
import com.microapp.customer.domain.Customer;
import com.microapp.customer.domain.Customer.Registration;
import org.junit.jupiter.api.Test;

public class CustomerEntityTest {

  @Test
  public void fullLifecycleVisitorToCustomer() {
    var testKit = EventSourcedTestKit.of("visitor-1", CustomerEntity::new);

    var r1 = testKit.method(CustomerEntity::createVisitor).invoke("MOBILE");
    assertThat(r1.isReply()).isTrue();
    Customer afterVisitor = testKit.getState();
    assertThat(afterVisitor.stage()).isEqualTo(VISITOR);
    assertThat(afterVisitor.customerId()).isEqualTo("visitor-1");

    var r2 = testKit
        .method(CustomerEntity::register)
        .invoke(new Registration("sara@example.my", "0123456789", "MOBILE"));
    assertThat(r2.isReply()).isTrue();
    Customer afterReg = testKit.getState();
    assertThat(afterReg.stage()).isEqualTo(REGISTERED);
    assertThat(afterReg.offer()).isNotNull();
    assertThat(afterReg.offer().code()).isEqualTo("WELCOME-APP25"); // MOBILE channel rule
    assertThat(afterReg.accountId()).isEqualTo("acc-1001");

    var r3 = testKit
        .method(CustomerEntity::completeKyc)
        .invoke(new EkycRequest("NRIC", "900101-01-1234", true));
    assertThat(r3.isReply()).isTrue();
    Customer afterKyc = testKit.getState();
    assertThat(afterKyc.stage()).isEqualTo(CUSTOMER);
    assertThat(afterKyc.kycRef()).startsWith("kyc-");
  }

  @Test
  public void registerRequiresVisitorStage() {
    var testKit = EventSourcedTestKit.of("visitor-2", CustomerEntity::new);
    var r = testKit.method(CustomerEntity::register).invoke(new Registration("a@b.my", "0123", "WEB"));
    assertThat(r.isError()).isTrue();
    assertThat(r.getError()).contains("VISITOR");
  }

  @Test
  public void kycRequiresRegisteredStage() {
    var testKit = EventSourcedTestKit.of("visitor-3", CustomerEntity::new);
    testKit.method(CustomerEntity::createVisitor).invoke("WEB");
    var r = testKit.method(CustomerEntity::completeKyc).invoke(new EkycRequest("NRIC", "900101", true));
    assertThat(r.isError()).isTrue();
    assertThat(r.getError()).containsIgnoringCase("REGISTERED");
  }

  @Test
  public void kycRequiresConsent() {
    var testKit = EventSourcedTestKit.of("visitor-4", CustomerEntity::new);
    testKit.method(CustomerEntity::createVisitor).invoke("WEB");
    testKit.method(CustomerEntity::register).invoke(new Registration("a@b.my", "0123", "WEB"));
    var r = testKit.method(CustomerEntity::completeKyc).invoke(new EkycRequest("NRIC", "900101", false));
    assertThat(r.isError()).isTrue();
    assertThat(r.getError()).containsIgnoringCase("consent");
  }

  @Test
  public void createVisitorIsIdempotent() {
    var testKit = EventSourcedTestKit.of("visitor-5", CustomerEntity::new);
    testKit.method(CustomerEntity::createVisitor).invoke("WEB");
    var r2 = testKit.method(CustomerEntity::createVisitor).invoke("WEB");
    assertThat(r2.isReply()).isTrue();
    assertThat(testKit.getAllEvents()).hasSize(1); // second call persists nothing
    Customer state = testKit.getState();
    assertThat(state.stage()).isEqualTo(VISITOR);
  }
}
