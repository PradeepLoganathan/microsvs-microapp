package com.microapp.analysis;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.HttpResponses;
import akka.http.javadsl.model.HttpResponse;

import com.microapp.analysis.agent.TransactionAnalysisAgent;
import com.microapp.analysis.heuristic.HeuristicCategorizer;
import com.microapp.analysis.model.AnalysisSummary;
import com.microapp.analysis.store.AnalysisStore;

import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Principal.ALL (matching statement-service and product-service) so other Akka
// services — e.g. recommendation-service calling via httpClientFor — are allowed.
// INTERNET-only rejected service-principal calls with 403.
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/accounts")
public class AnalysisEndpoint extends AbstractHttpEndpoint {

    private static final Logger log = LoggerFactory.getLogger(AnalysisEndpoint.class);
    private static final String ANALYSIS_MODE = ConfigFactory.load().getString("analysis.mode");

    private final ComponentClient componentClient;
    private final HttpClient statementClient;

    public AnalysisEndpoint(ComponentClient componentClient, HttpClientProvider httpClientProvider) {
        this.componentClient = componentClient;
        this.statementClient = httpClientProvider.httpClientFor("statement-service");
    }

    @Post("/{accountId}/analysis/run")
    public HttpResponse runAnalysis(String accountId) {
        String statementId = requestContext().queryParams().getString("statementId").orElse("stmt-2025-12");
        String mode = ANALYSIS_MODE;

        log.info("Running analysis for account={} statement={} mode={}", accountId, statementId, mode);

        if ("agent".equals(mode)) {
            // Agent mode: delegate to the Akka Agent component (requires OPENAI_API_KEY)
            String sessionId = accountId + "-" + statementId;
            log.info("Agent mode requested for {}/{} — delegating to agent (session={})", accountId, statementId, sessionId);
            var result = componentClient.forAgent()
                .inSession(sessionId)
                .method(TransactionAnalysisAgent::analyze)
                .invoke(new TransactionAnalysisAgent.AnalyzeRequest(accountId, statementId));
            log.info("Agent analysis completed for {}/{}", accountId, statementId);
            return HttpResponses.ok(new RunResponse("completed", "Agent analysis finished: " + result));
        } else {
            // Heuristic mode: fetch statement data via cross-service call, then analyze
            String statementJson = fetchStatement(accountId, statementId);
            AnalysisSummary summary = HeuristicCategorizer.analyze(accountId, statementId, statementJson);
            AnalysisStore.save(accountId, statementId, summary);
            return HttpResponses.ok(new RunResponse("completed", "Heuristic analysis finished"));
        }
    }

    @Get("/{accountId}/analysis/summary")
    public HttpResponse getSummary(String accountId) {
        String statementId = requestContext().queryParams().getString("statementId").orElse("stmt-2025-12");
        AnalysisSummary summary = AnalysisStore.get(accountId, statementId);

        if (summary == null) {
            // Auto-run analysis if not yet computed
            String statementJson = fetchStatement(accountId, statementId);
            summary = HeuristicCategorizer.analyze(accountId, statementId, statementJson);
            AnalysisStore.save(accountId, statementId, summary);
        }

        return HttpResponses.ok(summary);
    }

    private String fetchStatement(String accountId, String statementId) {
        log.info("Fetching statement from statement-service: {}/{}", accountId, statementId);
        var response = statementClient
            .GET("/accounts/" + accountId + "/statements/" + statementId)
            .responseBodyAs(String.class)
            .invoke();
        return response.body();
    }

    public record RunResponse(String status, String message) {}
}
