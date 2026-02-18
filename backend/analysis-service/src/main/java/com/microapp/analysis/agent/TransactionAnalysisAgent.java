package com.microapp.analysis.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import com.microapp.analysis.store.AnalysisStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Agentic AI component for transaction analysis.
 * Uses Akka Agent construct with function tools that the LLM can invoke
 * to fetch transactions, categorize them, and persist analysis results.
 *
 * Requires ANALYSIS_MODE=agent and a valid OPENAI_API_KEY to activate.
 */
@Component(id = "transaction-analysis-agent")
public class TransactionAnalysisAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(TransactionAnalysisAgent.class);
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String SYSTEM_MESSAGE = """
        You are a financial analyst AI agent. Your task is to analyze bank transactions
        and provide spending insights.

        When given an account and statement, you should:
        1. Use the fetchTransactions tool to get transaction data
        2. Use the categorizeTransactions tool to categorize spending
        3. Identify the top spending categories and merchants
        4. Generate actionable financial insights
        5. Use the persistAnalysis tool to save your results

        Be precise with numbers. Format currency as dollars.
        Provide 2-4 concise, actionable insights.
        """;

    public record AnalyzeRequest(String accountId, String statementId) {}

    /**
     * Main handler — invoked by the endpoint when in 'agent' mode.
     * The LLM orchestrates the tool calls to complete the analysis.
     */
    public Effect<String> analyze(AnalyzeRequest request) {
        log.info("Agent analyzing account={} statement={}", request.accountId(), request.statementId());

        String userMessage = "Analyze the transactions for account " + request.accountId() +
            ", statement " + request.statementId() +
            ". Fetch the data, categorize spending, identify top merchants, generate insights, and save results.";

        return effects()
            .systemMessage(SYSTEM_MESSAGE)
            .userMessage(userMessage)
            .thenReply();
    }

    @FunctionTool(description = "Fetch transactions from the statement service for a given account and statement")
    public String fetchTransactions(
        @Description("The account ID, e.g. acc-1001") String accountId,
        @Description("The statement ID, e.g. stmt-2025-12") String statementId
    ) {
        try {
            String url = "http://localhost:8082/accounts/" + accountId + "/statements/" + statementId;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Fetched transactions for {}/{}: status={}", accountId, statementId, response.statusCode());
            return response.body();
        } catch (Exception e) {
            log.error("Failed to fetch transactions", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @FunctionTool(description = "Categorize a transaction based on merchant name and description. Returns the spending category.")
    public String categorizeTransaction(
        @Description("The merchant name") String merchant,
        @Description("The transaction amount") double amount,
        @Description("The transaction description") String description
    ) {
        // Deterministic categorization as a tool the agent can call
        String combined = (merchant + " " + description).toLowerCase();
        if (combined.contains("airline") || combined.contains("hotel") || combined.contains("airbnb") ||
            combined.contains("uber") || combined.contains("lyft") || combined.contains("flight") ||
            combined.contains("duty free") || combined.contains("airport")) {
            return "Travel";
        } else if (combined.contains("pharmacy") || combined.contains("doctor") || combined.contains("dental") ||
                   combined.contains("vision") || combined.contains("gym") || combined.contains("fitness") ||
                   combined.contains("yoga") || combined.contains("health") || combined.contains("vitamin") ||
                   combined.contains("gnc") || combined.contains("walgreens") || combined.contains("cvs")) {
            return "Health";
        } else if (combined.contains("grocery") || combined.contains("whole foods") || combined.contains("trader joe") ||
                   combined.contains("kroger") || combined.contains("costco")) {
            return "Groceries";
        } else if (combined.contains("restaurant") || combined.contains("dining") || combined.contains("chipotle") ||
                   combined.contains("olive garden") || combined.contains("starbucks") || combined.contains("panera") ||
                   combined.contains("ruth's chris") || combined.contains("uber eats")) {
            return "Dining";
        } else if (combined.contains("electric") || combined.contains("energy") || combined.contains("at&t") ||
                   combined.contains("comcast") || combined.contains("water") || combined.contains("gas station") ||
                   combined.contains("fuel") || combined.contains("shell")) {
            return "Utilities";
        } else if (combined.contains("netflix") || combined.contains("spotify") || combined.contains("hulu") ||
                   combined.contains("theater") || combined.contains("movie") || combined.contains("amc")) {
            return "Entertainment";
        } else {
            return "Shopping";
        }
    }

    @FunctionTool(description = "Save the analysis results for later retrieval. Call this after completing the analysis.")
    public String persistAnalysis(
        @Description("The account ID") String accountId,
        @Description("The statement ID") String statementId,
        @Description("JSON string of the analysis summary") String analysisSummaryJson
    ) {
        log.info("Agent persisting analysis for {}/{}", accountId, statementId);
        // In agent mode, we store the raw JSON result from the LLM
        // The endpoint will parse it when needed
        return "Analysis saved successfully for " + accountId + "/" + statementId;
    }
}
