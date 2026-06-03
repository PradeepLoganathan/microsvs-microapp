package com.microapp.statement.api;

import static akka.Done.done;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import com.microapp.statement.application.StatementEntity;
import com.microapp.statement.application.StatementsByAccountView;
import com.microapp.statement.domain.Statement;
import com.microapp.statement.domain.Transaction;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class StatementIntegrationTest extends TestKitSupport {

  private String uniqueAccountId() {
    return "acc-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private Statement createSampleStatement(String statementId, String accountId) {
    return new Statement(statementId, accountId, "2025-12-01", "2025-12-31", 555.30, 0,
        List.of(
            new Transaction("txn-001", "2025-12-02", "Delta Airlines", 487.50, "Travel", "Flight"),
            new Transaction("txn-002", "2025-12-05", "Whole Foods", 67.80, "Groceries", "Groceries")
        ));
  }

  private void createViaEntity(String statementId, Statement statement) {
    var result = componentClient
        .forEventSourcedEntity(statementId)
        .method(StatementEntity::create)
        .invoke(statement);
    assertEquals(done(), result);
  }

  private Statement getViaEntity(String statementId) {
    return componentClient
        .forEventSourcedEntity(statementId)
        .method(StatementEntity::getStatement)
        .invoke();
  }

  @Test
  public void shouldCreateStatementAndGetViaHttp() {
    String accountId = uniqueAccountId();
    String stmtId = "stmt-" + UUID.randomUUID().toString().substring(0, 8);
    createViaEntity(stmtId, createSampleStatement(stmtId, accountId));

    var response = httpClient
        .GET("/accounts/" + accountId + "/statements/" + stmtId)
        .responseBodyAs(Statement.class)
        .invoke();

    assertEquals(StatusCodes.OK, response.status());
    assertEquals(stmtId, response.body().statementId());
    assertEquals(accountId, response.body().accountId());
    assertEquals(2, response.body().transactions().size());
  }

  @Test
  public void shouldGetNonExistentStatementReturnError() {
    String accountId = uniqueAccountId();
    assertThrows(Exception.class, () ->
        httpClient
            .GET("/accounts/" + accountId + "/statements/nonexistent")
            .responseBodyAs(Statement.class)
            .invoke()
    );
  }

  @Test
  public void shouldAddTransactionViaHttp() {
    String accountId = uniqueAccountId();
    String stmtId = "stmt-" + UUID.randomUUID().toString().substring(0, 8);
    createViaEntity(stmtId, createSampleStatement(stmtId, accountId));

    Transaction newTxn = new Transaction(
        "txn-new", "2025-12-10", "Netflix", 15.99, "Entertainment", "Subscription");

    var response = httpClient
        .POST("/accounts/" + accountId + "/statements/" + stmtId + "/transactions")
        .withRequestBody(newTxn)
        .invoke();

    assertEquals(StatusCodes.CREATED, response.status());

    Statement updated = getViaEntity(stmtId);
    assertEquals(3, updated.transactions().size());
    assertEquals(571.29, updated.totalDebits(), 0.01);
  }

  @Test
  public void shouldListStatementsViaView() {
    String accountId = uniqueAccountId();
    String stmtId1 = "stmt-" + UUID.randomUUID().toString().substring(0, 8);
    String stmtId2 = "stmt-" + UUID.randomUUID().toString().substring(0, 8);

    createViaEntity(stmtId1, new Statement(stmtId1, accountId, "2025-12-01", "2025-12-31", 100, 0,
        List.of(new Transaction("t1", "2025-12-02", "Shop A", 100, "Shopping", "Stuff"))));
    createViaEntity(stmtId2, new Statement(stmtId2, accountId, "2026-01-01", "2026-01-31", 200, 0,
        List.of(new Transaction("t2", "2026-01-05", "Shop B", 200, "Shopping", "More stuff"))));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var summaries = componentClient.forView()
              .method(StatementsByAccountView::getByAccount)
              .invoke(accountId)
              .statements();
          assertThat(summaries).hasSize(2);
        });
  }

  @Test
  public void shouldUpdateViewAfterAddTransaction() {
    String accountId = uniqueAccountId();
    String stmtId = "stmt-" + UUID.randomUUID().toString().substring(0, 8);

    createViaEntity(stmtId, createSampleStatement(stmtId, accountId));

    // Wait for view to index the created statement
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var summaries = componentClient.forView()
              .method(StatementsByAccountView::getByAccount)
              .invoke(accountId)
              .statements();
          assertThat(summaries).hasSize(1);
          var summary = summaries.iterator().next();
          assertThat(summary.transactionCount()).isEqualTo(2);
          assertThat(summary.totalDebits()).isCloseTo(555.30, org.assertj.core.data.Offset.offset(0.01));
        });

    // Add a transaction
    componentClient.forEventSourcedEntity(stmtId)
        .method(StatementEntity::addTransaction)
        .invoke(new Transaction("txn-new", "2025-12-10", "Netflix", 15.99, "Entertainment", "Sub"));

    // View should update
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var summaries = componentClient.forView()
              .method(StatementsByAccountView::getByAccount)
              .invoke(accountId)
              .statements();
          var summary = summaries.iterator().next();
          assertThat(summary.transactionCount()).isEqualTo(3);
          assertThat(summary.totalDebits()).isCloseTo(571.29, org.assertj.core.data.Offset.offset(0.01));
        });
  }

  @Test
  public void shouldComputeBalanceFromStatements() {
    String accountId = uniqueAccountId();
    String stmtId1 = "stmt-" + UUID.randomUUID().toString().substring(0, 8);
    String stmtId2 = "stmt-" + UUID.randomUUID().toString().substring(0, 8);

    // Statement record order is (.., totalDebits, totalCredits, ..)
    createViaEntity(stmtId1, new Statement(stmtId1, accountId, "2025-12-01", "2025-12-31", 100, 500,
        List.of(new Transaction("t1", "2025-12-02", "Shop A", 100, "Shopping", "Stuff"))));
    createViaEntity(stmtId2, new Statement(stmtId2, accountId, "2026-01-01", "2026-01-31", 200, 500,
        List.of(new Transaction("t2", "2026-01-05", "Shop B", 200, "Shopping", "More stuff"))));

    // Balance reads the (eventually consistent) view, so await indexing.
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var balance = httpClient
              .GET("/accounts/" + accountId + "/balance")
              .responseBodyAs(StatementEndpoint.BalanceView.class)
              .invoke();
          assertEquals(StatusCodes.OK, balance.status());
          // (500-100) + (500-200) = 700
          assertThat(balance.body().currentBalance()).isCloseTo(700.0, org.assertj.core.data.Offset.offset(0.01));
          assertThat(balance.body().asOf()).isEqualTo("2026-01-31");
        });
  }

  @Test
  public void shouldReturnZeroBalanceForUnknownAccount() {
    var balance = httpClient
        .GET("/accounts/" + uniqueAccountId() + "/balance")
        .responseBodyAs(StatementEndpoint.BalanceView.class)
        .invoke();

    assertEquals(StatusCodes.OK, balance.status());
    assertThat(balance.body().currentBalance()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
    assertThat(balance.body().asOf()).isEmpty();
  }

  @Test
  public void shouldOpenAccountWithOpeningBalance() {
    String accountId = uniqueAccountId();

    var openResponse = httpClient
        .POST("/accounts/" + accountId + "/open")
        .withRequestBody(new StatementEndpoint.OpenRequest("CASA Account", 1500.0))
        .invoke();
    assertEquals(StatusCodes.CREATED, openResponse.status());

    // The opening statement gives the new account both a balance and a listed statement.
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var balance = httpClient
              .GET("/accounts/" + accountId + "/balance")
              .responseBodyAs(StatementEndpoint.BalanceView.class)
              .invoke();
          assertThat(balance.body().currentBalance()).isCloseTo(1500.0, org.assertj.core.data.Offset.offset(0.01));

          var summaries = componentClient.forView()
              .method(StatementsByAccountView::getByAccount)
              .invoke(accountId)
              .statements();
          assertThat(summaries).hasSize(1);
          assertThat(summaries.iterator().next().statementId()).isEqualTo("stmt-open-" + accountId);
        });
  }

  @Test
  public void shouldAppendDebitAndCreditToCurrentStatement() {
    String accountId = uniqueAccountId();

    var debit = httpClient
        .POST("/accounts/" + accountId + "/transactions")
        .withRequestBody(new Transaction("t-d1", "2026-06-03", "Shop", 100.0, "Shopping", "buy", "DEBIT"))
        .invoke();
    assertEquals(StatusCodes.CREATED, debit.status());

    var credit = httpClient
        .POST("/accounts/" + accountId + "/transactions")
        .withRequestBody(new Transaction("t-c1", "2026-06-03", "Transfer In", 1000.0, "Transfer", "in", "CREDIT"))
        .invoke();
    assertEquals(StatusCodes.CREATED, credit.status());

    // Balance = credits - debits = 1000 - 100 = 900 (credit routed correctly, not to debits).
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var balance = httpClient
              .GET("/accounts/" + accountId + "/balance")
              .responseBodyAs(StatementEndpoint.BalanceView.class)
              .invoke();
          assertThat(balance.body().currentBalance()).isCloseTo(900.0, org.assertj.core.data.Offset.offset(0.01));
        });
  }

  @Test
  public void shouldSeedStatements() {
    var response = httpClient.POST("/accounts/seed").invoke();
    assertEquals(StatusCodes.OK, response.status());

    // 15 debits + 2 credits (salary + transfer) per month
    Statement dec = getViaEntity("stmt-2025-12");
    assertEquals("acc-1001", dec.accountId());
    assertEquals(17, dec.transactions().size());
    assertEquals(2, dec.transactions().stream().filter(Transaction::isCredit).count());

    Statement jan = getViaEntity("stmt-2026-01");
    assertEquals(17, jan.transactions().size());

    Statement feb = getViaEntity("stmt-2026-02");
    assertEquals(17, feb.transactions().size());
  }

  @Test
  public void shouldViewReturnStatementsFilteredByAccount() {
    String accountA = uniqueAccountId();
    String accountB = uniqueAccountId();

    String stmtA = "stmt-" + UUID.randomUUID().toString().substring(0, 8);
    String stmtB = "stmt-" + UUID.randomUUID().toString().substring(0, 8);

    createViaEntity(stmtA, new Statement(stmtA, accountA, "2025-12-01", "2025-12-31", 100, 0,
        List.of(new Transaction("t1", "2025-12-02", "Shop", 100, "Shopping", "Stuff"))));
    createViaEntity(stmtB, new Statement(stmtB, accountB, "2025-12-01", "2025-12-31", 200, 0,
        List.of(new Transaction("t2", "2025-12-03", "Store", 200, "Shopping", "Things"))));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var summariesA = componentClient.forView()
              .method(StatementsByAccountView::getByAccount)
              .invoke(accountA)
              .statements();
          assertThat(summariesA).hasSize(1);
          assertThat(summariesA.iterator().next().totalDebits()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.01));

          var summariesB = componentClient.forView()
              .method(StatementsByAccountView::getByAccount)
              .invoke(accountB)
              .statements();
          assertThat(summariesB).hasSize(1);
          assertThat(summariesB.iterator().next().totalDebits()).isCloseTo(200.0, org.assertj.core.data.Offset.offset(0.01));
        });
  }
}
