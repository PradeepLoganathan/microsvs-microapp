package com.microapp.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import com.microapp.payment.domain.Beneficiary;
import org.junit.jupiter.api.Test;

/**
 * Covers the catalog + the validation paths that short-circuit before any
 * cross-service call. A successful transfer posts to statement-service, which
 * isn't part of this isolated test kit — that path is verified end-to-end.
 */
public class PaymentEndpointIntegrationTest extends TestKitSupport {

  @Test
  public void shouldListMockBeneficiaries() {
    var response = httpClient
        .GET("/payments/beneficiaries")
        .responseBodyAs(Beneficiary[].class)
        .invoke();

    assertEquals(StatusCodes.OK, response.status());
    assertThat(response.body()).hasSizeGreaterThanOrEqualTo(3);
    assertThat(response.body()).anyMatch(b -> b.name().equals("Ahmad Zaki"));
  }

  @Test
  public void shouldRejectZeroAmount() {
    var result = transfer(new PaymentEndpoint.TransferRequest("acc-1001", "CASA-X", null, 0, "x"));
    assertThat(result.ok()).isFalse();
    assertThat(result.message()).contains("greater than zero");
  }

  @Test
  public void shouldRejectMissingSource() {
    var result = transfer(new PaymentEndpoint.TransferRequest("", "CASA-X", null, 100, "x"));
    assertThat(result.ok()).isFalse();
    assertThat(result.message()).contains("Source account");
  }

  @Test
  public void shouldRejectUnknownBeneficiary() {
    var result = transfer(new PaymentEndpoint.TransferRequest("acc-1001", null, "ben-nope", 100, "x"));
    assertThat(result.ok()).isFalse();
    assertThat(result.message()).contains("Unknown beneficiary");
  }

  private PaymentEndpoint.TransferResult transfer(PaymentEndpoint.TransferRequest req) {
    return httpClient
        .POST("/payments/transfer")
        .withRequestBody(req)
        .responseBodyAs(PaymentEndpoint.TransferResult.class)
        .invoke()
        .body();
  }
}
