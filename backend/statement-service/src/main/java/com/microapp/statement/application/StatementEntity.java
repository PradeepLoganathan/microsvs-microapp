package com.microapp.statement.application;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.microapp.statement.domain.Statement;
import com.microapp.statement.domain.StatementEvent;
import com.microapp.statement.domain.StatementEvent.StatementCreated;
import com.microapp.statement.domain.StatementEvent.TransactionAdded;
import com.microapp.statement.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

@Component(id = "statement")
public class StatementEntity extends EventSourcedEntity<Statement, StatementEvent> {

  private static final Logger logger = LoggerFactory.getLogger(StatementEntity.class);

  public ReadOnlyEffect<Statement> getStatement() {
    if (currentState() == null) {
      return effects().error("No statement found for id '" + commandContext().entityId() + "'");
    }
    return effects().reply(currentState());
  }

  public Effect<Done> create(Statement statement) {
    if (currentState() != null) {
      // Already exists — idempotent
      return effects().reply(done());
    }
    logger.info("Creating statement '{}' for account '{}'",
        statement.statementId(), statement.accountId());
    return effects()
        .persist(new StatementCreated(
            statement.statementId(),
            statement.accountId(),
            statement.periodStart(),
            statement.periodEnd(),
            statement.totalDebits(),
            statement.totalCredits(),
            statement.transactions()))
        .thenReply(__ -> done());
  }

  public Effect<Done> addTransaction(Transaction transaction) {
    if (currentState() == null) {
      return effects().error("Statement '" + commandContext().entityId() + "' does not exist");
    }
    logger.info("Adding transaction '{}' to statement '{}'",
        transaction.id(), commandContext().entityId());
    return effects()
        .persist(new TransactionAdded(transaction))
        .thenReply(__ -> done());
  }

  @Override
  public Statement applyEvent(StatementEvent event) {
    return switch (event) {
      case StatementCreated created -> new Statement(
          created.statementId(),
          created.accountId(),
          created.periodStart(),
          created.periodEnd(),
          created.totalDebits(),
          created.totalCredits(),
          created.transactions()
      );
      case TransactionAdded added -> {
        var updatedTxns = new ArrayList<>(currentState().transactions());
        updatedTxns.add(added.transaction());
        yield new Statement(
            currentState().statementId(),
            currentState().accountId(),
            currentState().periodStart(),
            currentState().periodEnd(),
            currentState().totalDebits() + added.transaction().amount(),
            currentState().totalCredits(),
            updatedTxns
        );
      }
    };
  }
}
