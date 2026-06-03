package com.microapp.payment.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import com.microapp.payment.domain.Beneficiary;
import com.microapp.payment.domain.BeneficiaryCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/payments")
public class PaymentEndpoint extends AbstractHttpEndpoint {

  private static final Logger log = LoggerFactory.getLogger(PaymentEndpoint.class);

  /** Transfer instruction. Exactly one of toAccountId / beneficiaryId should be set. */
  public record TransferRequest(
      String fromAccountId, String toAccountId, String beneficiaryId, double amount, String note) {}

  public record TransferResult(
      boolean ok, String transferId, String fromAccountId, String to, double amount, String message) {}

  /** Mirrors statement-service's Transaction JSON shape. */
  private record Txn(
      String id, String date, String merchant, double amount,
      String category, String description, String direction) {}

  private final HttpClient statementClient;

  public PaymentEndpoint(HttpClientProvider httpClientProvider) {
    this.statementClient = httpClientProvider.httpClientFor("statement-service");
  }

  @Get("/beneficiaries")
  public List<Beneficiary> beneficiaries() {
    return BeneficiaryCatalog.ALL;
  }

  @Post("/transfer")
  public TransferResult transfer(TransferRequest req) {
    if (req.fromAccountId() == null || req.fromAccountId().isBlank()) {
      return failed(req, "Source account is required");
    }
    if (req.amount() <= 0) {
      return failed(req, "Amount must be greater than zero");
    }

    // Resolve the destination — an own account or a saved beneficiary.
    boolean ownAccount = req.toAccountId() != null && !req.toAccountId().isBlank();
    String destLabel;
    if (ownAccount) {
      destLabel = req.toAccountId();
    } else {
      var beneficiary = BeneficiaryCatalog.byId(req.beneficiaryId());
      if (beneficiary.isEmpty()) {
        return failed(req, "Unknown beneficiary");
      }
      destLabel = beneficiary.get().name();
    }

    var transferId = "txf-" + UUID.randomUUID().toString().substring(0, 8);
    var today = LocalDate.now().toString();
    var note = req.note() == null ? "" : req.note();

    // Debit the source.
    postTxn(req.fromAccountId(), new Txn(
        transferId + "-d", today, "Transfer to " + destLabel, req.amount(),
        "Transfer", note, "DEBIT"));

    // Credit the destination only when it's one of the customer's own accounts.
    if (ownAccount) {
      postTxn(req.toAccountId(), new Txn(
          transferId + "-c", today, "Transfer from " + req.fromAccountId(), req.amount(),
          "Transfer", note, "CREDIT"));
    }

    log.info("Transfer {} of {} from {} to {} ({})",
        transferId, req.amount(), req.fromAccountId(), destLabel, ownAccount ? "own account" : "beneficiary");
    return new TransferResult(true, transferId, req.fromAccountId(), destLabel, req.amount(),
        "Transfer completed");
  }

  private void postTxn(String accountId, Txn txn) {
    statementClient
        .POST("/accounts/" + accountId + "/transactions")
        .withRequestBody(txn)
        .invoke();
  }

  private static TransferResult failed(TransferRequest req, String message) {
    return new TransferResult(false, "", req.fromAccountId(), "", req.amount(), message);
  }
}
