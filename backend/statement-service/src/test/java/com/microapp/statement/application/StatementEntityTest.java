package com.microapp.statement.application;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.Done;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import com.microapp.statement.domain.Statement;
import com.microapp.statement.domain.StatementEvent;
import com.microapp.statement.domain.StatementEvent.StatementCreated;
import com.microapp.statement.domain.StatementEvent.TransactionAdded;
import com.microapp.statement.domain.Transaction;
import java.util.List;
import org.junit.jupiter.api.Test;

public class StatementEntityTest {

  private final Transaction txn1 = new Transaction(
      "txn-001", "2025-12-02", "Delta Airlines", 487.50, "Travel", "Round trip ATL-LAX");
  private final Transaction txn2 = new Transaction(
      "txn-002", "2025-12-05", "Whole Foods", 67.80, "Groceries", "Weekly groceries");

  private final Statement sampleStatement = new Statement(
      "stmt-2025-12", "acc-1001", "2025-12-01", "2025-12-31",
      555.30, 0, List.of(txn1, txn2));

  @Test
  public void shouldCreateStatement() {
    var testKit = EventSourcedTestKit.of(StatementEntity::new);

    EventSourcedResult<Done> result = testKit
        .method(StatementEntity::create)
        .invoke(sampleStatement);

    assertEquals(done(), result.getReply());

    StatementCreated created = result.getNextEventOfType(StatementCreated.class);
    assertEquals("stmt-2025-12", created.statementId());
    assertEquals("acc-1001", created.accountId());
    assertEquals("2025-12-01", created.periodStart());
    assertEquals("2025-12-31", created.periodEnd());
    assertEquals(555.30, created.totalDebits(), 0.01);
    assertEquals(2, created.transactions().size());

    Statement state = testKit.getState();
    assertNotNull(state);
    assertEquals("stmt-2025-12", state.statementId());
    assertEquals("acc-1001", state.accountId());
    assertEquals(2, state.transactions().size());
  }

  @Test
  public void shouldBeIdempotentOnDuplicateCreate() {
    var testKit = EventSourcedTestKit.of(StatementEntity::new);

    testKit.method(StatementEntity::create).invoke(sampleStatement);

    EventSourcedResult<Done> result = testKit
        .method(StatementEntity::create)
        .invoke(sampleStatement);

    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().isEmpty());
  }

  @Test
  public void shouldGetStatement() {
    var testKit = EventSourcedTestKit.of(StatementEntity::new);

    testKit.method(StatementEntity::create).invoke(sampleStatement);

    EventSourcedResult<Statement> result = testKit
        .method(StatementEntity::getStatement)
        .invoke();

    Statement stmt = result.getReply();
    assertEquals("stmt-2025-12", stmt.statementId());
    assertEquals("acc-1001", stmt.accountId());
    assertEquals(2, stmt.transactions().size());
    assertEquals("Delta Airlines", stmt.transactions().get(0).merchant());
  }

  @Test
  public void shouldFailGetOnNonExistentStatement() {
    var testKit = EventSourcedTestKit.of(StatementEntity::new);

    EventSourcedResult<Statement> result = testKit
        .method(StatementEntity::getStatement)
        .invoke();

    assertTrue(result.isError());
  }

  @Test
  public void shouldAddTransaction() {
    var testKit = EventSourcedTestKit.of(StatementEntity::new);

    testKit.method(StatementEntity::create).invoke(sampleStatement);

    Transaction newTxn = new Transaction(
        "txn-003", "2025-12-10", "Netflix", 15.99, "Entertainment", "Monthly subscription");

    EventSourcedResult<Done> result = testKit
        .method(StatementEntity::addTransaction)
        .invoke(newTxn);

    assertEquals(done(), result.getReply());

    TransactionAdded event = result.getNextEventOfType(TransactionAdded.class);
    assertEquals("txn-003", event.transaction().id());
    assertEquals(15.99, event.transaction().amount(), 0.01);

    Statement state = testKit.getState();
    assertEquals(3, state.transactions().size());
    assertEquals(571.29, state.totalDebits(), 0.01);
    assertEquals("Netflix", state.transactions().get(2).merchant());
  }

  @Test
  public void shouldFailAddTransactionOnNonExistentStatement() {
    var testKit = EventSourcedTestKit.of(StatementEntity::new);

    Transaction txn = new Transaction(
        "txn-999", "2025-12-10", "Netflix", 15.99, "Entertainment", "Subscription");

    EventSourcedResult<Done> result = testKit
        .method(StatementEntity::addTransaction)
        .invoke(txn);

    assertTrue(result.isError());
  }

  @Test
  public void shouldAddMultipleTransactions() {
    var testKit = EventSourcedTestKit.of(StatementEntity::new);

    testKit.method(StatementEntity::create).invoke(sampleStatement);

    Transaction txn3 = new Transaction(
        "txn-003", "2025-12-10", "Netflix", 15.99, "Entertainment", "Subscription");
    Transaction txn4 = new Transaction(
        "txn-004", "2025-12-15", "Uber", 42.30, "Travel", "Airport transfer");

    testKit.method(StatementEntity::addTransaction).invoke(txn3);
    testKit.method(StatementEntity::addTransaction).invoke(txn4);

    Statement state = testKit.getState();
    assertEquals(4, state.transactions().size());
    assertEquals(555.30 + 15.99 + 42.30, state.totalDebits(), 0.01);
  }

  @Test
  public void shouldPreserveStatementMetadataOnAddTransaction() {
    var testKit = EventSourcedTestKit.of(StatementEntity::new);

    testKit.method(StatementEntity::create).invoke(sampleStatement);

    Transaction newTxn = new Transaction(
        "txn-003", "2025-12-10", "Netflix", 15.99, "Entertainment", "Subscription");

    testKit.method(StatementEntity::addTransaction).invoke(newTxn);

    Statement state = testKit.getState();
    assertEquals("stmt-2025-12", state.statementId());
    assertEquals("acc-1001", state.accountId());
    assertEquals("2025-12-01", state.periodStart());
    assertEquals("2025-12-31", state.periodEnd());
    assertEquals(0, state.totalCredits(), 0.01);
  }
}
