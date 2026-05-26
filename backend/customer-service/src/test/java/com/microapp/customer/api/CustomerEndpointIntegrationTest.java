package com.microapp.customer.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import com.microapp.customer.api.CustomerEndpoint.CustomerView;
import com.microapp.customer.api.CustomerEndpoint.KycRequest;
import com.microapp.customer.api.CustomerEndpoint.PreLoginContent;
import com.microapp.customer.api.CustomerEndpoint.RegisterRequest;
import com.microapp.customer.api.CustomerEndpoint.VisitorRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class CustomerEndpointIntegrationTest extends TestKitSupport {

  /** Cold open → register (with welcome offer) → eKYC → CUSTOMER, on one id, over HTTP. */
  @Test
  public void coldOpenToCustomerOverHttp() {
    var id = "visitor-http-" + UUID.randomUUID();

    var visitor = httpClient.POST("/customers/" + id + "/visitor")
        .withRequestBody(new VisitorRequest("MOBILE"))
        .responseBodyAs(CustomerView.class).invoke();
    assertThat(visitor.status().isSuccess()).isTrue();
    assertThat(visitor.body().stage()).isEqualTo("VISITOR");

    var registered = httpClient.POST("/customers/" + id + "/register")
        .withRequestBody(new RegisterRequest("sara@example.my", "0123456789", "MOBILE"))
        .responseBodyAs(CustomerView.class).invoke();
    assertThat(registered.body().stage()).isEqualTo("REGISTERED");
    assertThat(registered.body().offer()).isNotNull();
    assertThat(registered.body().offer().code()).isEqualTo("WELCOME-APP25");

    var customer = httpClient.POST("/customers/" + id + "/kyc")
        .withRequestBody(new KycRequest("NRIC", "900101-01-1234", true))
        .responseBodyAs(CustomerView.class).invoke();
    assertThat(customer.body().stage()).isEqualTo("CUSTOMER");
    assertThat(customer.body().accountId()).isEqualTo("acc-1001");
    assertThat(customer.body().kycRef()).startsWith("kyc-");
  }

  @Test
  public void preloginReturnsTiles() {
    var resp = httpClient.GET("/customers/prelogin").responseBodyAs(PreLoginContent.class).invoke();
    assertThat(resp.status().isSuccess()).isTrue();
    assertThat(resp.body().tiles()).isNotEmpty();
  }
}
