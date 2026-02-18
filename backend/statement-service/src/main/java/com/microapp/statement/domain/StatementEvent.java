package com.microapp.statement.domain;

import akka.javasdk.annotations.TypeName;
import java.util.List;

public sealed interface StatementEvent {

  @TypeName("statement-created")
  record StatementCreated(
      String statementId,
      String accountId,
      String periodStart,
      String periodEnd,
      double totalDebits,
      double totalCredits,
      List<Transaction> transactions
  ) implements StatementEvent {}

  @TypeName("transaction-added")
  record TransactionAdded(
      Transaction transaction
  ) implements StatementEvent {}
}
