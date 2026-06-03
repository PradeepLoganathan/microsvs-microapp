package com.microapp.statement.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.microapp.statement.domain.StatementEvent;
import com.microapp.statement.domain.StatementEvent.StatementCreated;
import com.microapp.statement.domain.StatementEvent.TransactionAdded;
import com.microapp.statement.domain.StatementSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "statements-by-account")
public class StatementsByAccountView extends View {

  private static final Logger logger = LoggerFactory.getLogger(StatementsByAccountView.class);

  @Consume.FromEventSourcedEntity(StatementEntity.class)
  public static class StatementSummaryUpdater extends TableUpdater<StatementSummary> {

    public Effect<StatementSummary> onEvent(StatementEvent event) {
      return switch (event) {
        case StatementCreated created -> {
          logger.info("View indexing statement '{}' for account '{}'",
              created.statementId(), created.accountId());
          yield effects().updateRow(new StatementSummary(
              created.statementId(),
              created.accountId(),
              created.periodStart(),
              created.periodEnd(),
              created.totalDebits(),
              created.transactions().size()
          ));
        }
        case TransactionAdded added -> {
          var current = rowState();
          var txn = added.transaction();
          logger.info("View updating statement '{}' with new transaction '{}'",
              current.statementId(), txn.id());
          yield effects().updateRow(new StatementSummary(
              current.statementId(),
              current.accountId(),
              current.periodStart(),
              current.periodEnd(),
              current.totalDebits() + (txn.isCredit() ? 0.0 : txn.amount()),
              current.transactionCount() + 1
          ));
        }
      };
    }
  }

  @Query("SELECT * AS statements FROM statements_by_account WHERE accountId = :accountId")
  public QueryEffect<StatementSummaries> getByAccount(String accountId) {
    return queryResult();
  }
}
