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

  @Post("/{accountId}/statements/{statementId}/transactions")
  public HttpResponse addTransaction(String accountId, String statementId, Transaction transaction) {
    logger.info("Adding transaction '{}' to statement '{}'", transaction.id(), statementId);
    componentClient
        .forEventSourcedEntity(statementId)
        .method(StatementEntity::addTransaction)
        .invoke(transaction);
    return HttpResponses.created();
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
