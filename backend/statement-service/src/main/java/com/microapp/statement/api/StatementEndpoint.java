package com.microapp.statement.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import com.microapp.statement.application.StatementEntity;
import com.microapp.statement.application.StatementsByAccountView;
import com.microapp.statement.domain.Statement;
import com.microapp.statement.domain.StatementSummary;
import com.microapp.statement.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/accounts")
public class StatementEndpoint extends AbstractHttpEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(StatementEndpoint.class);

  private final ComponentClient componentClient;

  public StatementEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  /** Current cash position of an account, derived from its statements. */
  public record BalanceView(String accountId, double currentBalance, String asOf) {}

  /** Body for opening an account's first statement (opening balance). */
  public record OpenRequest(String productName, Double openingBalance) {}

  @Get("/{accountId}/statements")
  public Collection<StatementSummary> getStatements(String accountId) {
    return componentClient
        .forView()
        .method(StatementsByAccountView::getByAccount)
        .invoke(accountId)
        .statements();
  }

  @Get("/{accountId}/statements/{statementId}")
  public Statement getStatement(String accountId, String statementId) {
    return componentClient
        .forEventSourcedEntity(statementId)
        .method(StatementEntity::getStatement)
        .invoke();
  }

  @Get("/{accountId}/transactions")
  public List<Transaction> getTransactions(String accountId) {
    String from = requestContext().queryParams().getString("from").orElse(null);
    String to = requestContext().queryParams().getString("to").orElse(null);

    // Get all statement IDs for this account, then collect transactions
    var summaries = componentClient
        .forView()
        .method(StatementsByAccountView::getByAccount)
        .invoke(accountId)
        .statements();

    return summaries.stream()
        .map(summary -> componentClient
            .forEventSourcedEntity(summary.statementId())
            .method(StatementEntity::getStatement)
            .invoke())
        .flatMap(stmt -> stmt.transactions().stream())
        .filter(t -> from == null || t.date().compareTo(from) >= 0)
        .filter(t -> to == null || t.date().compareTo(to) <= 0)
        .sorted((a, b) -> a.date().compareTo(b.date()))
        .toList();
  }

  @Get("/{accountId}/balance")
  public BalanceView getBalance(String accountId) {
    var summaries = componentClient
        .forView()
        .method(StatementsByAccountView::getByAccount)
        .invoke(accountId)
        .statements();

    double balance = 0.0;
    String asOf = "";
    for (var summary : summaries) {
      var statement = componentClient
          .forEventSourcedEntity(summary.statementId())
          .method(StatementEntity::getStatement)
          .invoke();
      balance += statement.totalCredits() - statement.totalDebits();
      if (statement.periodEnd().compareTo(asOf) > 0) {
        asOf = statement.periodEnd();
      }
    }
    return new BalanceView(accountId, Math.round(balance * 100.0) / 100.0, asOf);
  }

  @Post("/{accountId}/open")
  public HttpResponse open(String accountId, OpenRequest request) {
    double openingBalance = request.openingBalance() == null ? 0.0 : request.openingBalance();
    var today = LocalDate.now();
    var statementId = "stmt-open-" + accountId;

    // Opening statement: the opening balance is a credit, no spending yet. Kept
    // transaction-free so the opening credit never leaks into spending/analysis.
    var statement = new Statement(
        statementId, accountId,
        today.withDayOfMonth(1).toString(), today.toString(),
        0.0, openingBalance, List.of());

    logger.info("Opening account '{}' with opening balance {}", accountId, openingBalance);
    componentClient
        .forEventSourcedEntity(statementId)
        .method(StatementEntity::create)
        .invoke(statement);
    return HttpResponses.created();
  }

  @Post("/{accountId}/transactions")
  public HttpResponse addCurrentTransaction(String accountId, Transaction transaction) {
    var today = LocalDate.now();
    var statementId = "stmt-" + accountId + "-" + today.toString().substring(0, 7); // stmt-<acct>-YYYY-MM

    // Ensure the current-month statement exists (idempotent), then append the txn.
    var skeleton = new Statement(
        statementId, accountId,
        today.withDayOfMonth(1).toString(),
        today.withDayOfMonth(today.lengthOfMonth()).toString(),
        0.0, 0.0, List.of());
    componentClient.forEventSourcedEntity(statementId)
        .method(StatementEntity::create).invoke(skeleton);

    logger.info("Appending {} txn '{}' to current statement '{}'",
        transaction.direction(), transaction.id(), statementId);
    componentClient.forEventSourcedEntity(statementId)
        .method(StatementEntity::addTransaction).invoke(transaction);
    return HttpResponses.created();
  }

  @Post("/{accountId}/statements/{statementId}/transactions")
  public HttpResponse addTransaction(String accountId, String statementId, Transaction transaction) {
    logger.info("Adding transaction '{}' to statement '{}'", transaction.id(), statementId);
    componentClient
        .forEventSourcedEntity(statementId)
        .method(StatementEntity::addTransaction)
        .invoke(transaction);
    return HttpResponses.created();
  }

  @Post("/{accountId}/seed-demo")
  public HttpResponse seedDemo(String accountId) {
    logger.info("Seeding demo statements for account '{}'", accountId);
    MockDataProvider.getDemoStatementsFor(accountId).forEach(statement ->
        componentClient
            .forEventSourcedEntity(statement.statementId())
            .method(StatementEntity::create)
            .invoke(statement)
    );
    return HttpResponses.ok();
  }

  @Post("/seed")
  public HttpResponse seed() {
    logger.info("Seeding statement data...");
    MockDataProvider.getAllStatements().forEach(statement ->
        componentClient
            .forEventSourcedEntity(statement.statementId())
            .method(StatementEntity::create)
            .invoke(statement)
    );
    logger.info("Statement data seeded successfully");
    return HttpResponses.ok();
  }
}
