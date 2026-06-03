package com.microapp.onboarding.application;

import static com.microapp.onboarding.domain.CasaOnboarding.CasaStage.COMPLETED;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import com.microapp.onboarding.domain.CasaOnboarding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When a CASA onboarding workflow reaches COMPLETED, opens the customer's first
 * statement in statement-service so the freshly-minted account has a balance and
 * a linked statement (otherwise the opened account is invisible everywhere except
 * the onboarding view).
 *
 * Best-effort by design: statement-service may not be running in a single-service
 * local run, so a failed call is logged and ignored rather than retried forever.
 * statement-service's {@code /open} is idempotent, so at-least-once redelivery of
 * the COMPLETED state is safe.
 */
@Component(id = "casa-account-opener")
@Consume.FromWorkflow(CasaOnboardingWorkflow.class)
public class AccountOpeningConsumer extends Consumer {

  private static final Logger log = LoggerFactory.getLogger(AccountOpeningConsumer.class);

  /** Mirrors statement-service's OpenRequest JSON shape. */
  private record OpenAccountRequest(String productName, Double openingBalance) {}

  private final HttpClient statementClient;

  public AccountOpeningConsumer(HttpClientProvider httpClientProvider) {
    this.statementClient = httpClientProvider.httpClientFor("statement-service");
  }

  public Effect onChange(CasaOnboarding state) {
    if (state.stage() != COMPLETED || state.accountId() == null || state.accountId().isBlank()) {
      return effects().ignore();
    }

    var productName = state.details() != null ? state.details().accountType() : "CASA";
    try {
      statementClient
          .POST("/accounts/" + state.accountId() + "/open")
          .withRequestBody(new OpenAccountRequest(productName, 0.0))
          .invoke();
      log.info("Opened statement account for CASA {} (application {})",
          state.accountId(), state.applicationId());
    } catch (Exception e) {
      log.warn("Could not open statement account for {} (statement-service unreachable?): {}",
          state.accountId(), e.getMessage());
    }
    return effects().done();
  }
}
