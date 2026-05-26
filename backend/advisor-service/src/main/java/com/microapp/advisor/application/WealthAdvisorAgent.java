package com.microapp.advisor.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microapp.advisor.domain.AffordabilityProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Conversational, Shariah-aware wealth advisor (K1).
 *
 * One command handler ({@link #ask}) drives a multi-turn conversation via session
 * memory (session id supplied by the caller, per customer). The LLM grounds its
 * answers by calling the {@code @FunctionTool} methods below, which reach into the
 * other services over HTTP. It may PROPOSE one action but never executes money
 * movement — consequential actions are deferred to a human via requestHumanHandoff.
 */
@Component(id = "wealth-advisor")
public class WealthAdvisorAgent extends Agent {

  private static final Logger log = LoggerFactory.getLogger(WealthAdvisorAgent.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String SYSTEM_MESSAGE =
      """
      You are "K", a warm, Shariah-aware wealth advisor for an Islamic bank.
      You help customers save, budget, and choose suitable products.

      Rules:
      - ALWAYS ground financial figures in the customer's real data. Use the tools:
        getAccountSummary (balances in/out + estimated monthly surplus),
        getSpendingProfile (spend by category), getGoals (existing savings goals),
        projectAffordability (is a target reachable?), listProducts (catalog).
      - Never invent numbers. Use the values returned by the tools.
      - Propose at most ONE concrete next action. Prefer halal / Shariah-compliant options.
      - You may PROPOSE actions, but you must NEVER move money or open a product yourself.
        If the customer wants to proceed, call requestHumanHandoff and set
        humanHandoffOffered = true.
      - Keep replies concise, friendly, and practical.

      Filling the response:
      - reply: your conversational answer, including the real numbers.
      - proposal: ALWAYS include this object. When you are proposing a specific action,
        set actionType (e.g. CREATE_TABUNG_GOAL, OPEN_TAKAFUL, OPEN_PRODUCT) and fill the
        fields. When you are NOT proposing anything, set actionType to "NONE" and leave the
        other fields empty (0 or "").
      - humanHandoffOffered: true when you have offered or logged a human follow-up.
      """;

  // ---- message model (inner records) ----

  public record Question(String customerId, String message) {}

  public record Proposal(
      @Description("Action type: CREATE_TABUNG_GOAL, OPEN_TAKAFUL, OPEN_PRODUCT, or NONE when not proposing")
      String actionType,
      @Description("Short title for the proposed action")
      String title,
      @Description("One or two sentence description of the proposal")
      String description,
      @Description("Target amount, if relevant; otherwise 0")
      double targetAmount,
      @Description("Suggested monthly contribution, if relevant; otherwise 0")
      double monthlyContribution,
      @Description("Related product id from the catalog, if any; otherwise empty")
      String productId,
      @Description("Why this is being proposed, grounded in the customer's data")
      String rationale) {}

  public record AdvisorResponse(
      @Description("Conversational answer to the customer, including real figures")
      String reply,
      @Description("Always present; actionType=NONE when not proposing an action")
      Proposal proposal,
      @Description("True when a human advisor follow-up has been offered or logged")
      boolean humanHandoffOffered) {}

  // ---- cross-service HTTP clients ----

  private final HttpClient statementClient;
  private final HttpClient analysisClient;
  private final HttpClient productClient;

  public WealthAdvisorAgent(HttpClientProvider httpClientProvider) {
    this.statementClient = httpClientProvider.httpClientFor("statement-service");
    this.analysisClient = httpClientProvider.httpClientFor("analysis-service");
    this.productClient = httpClientProvider.httpClientFor("product-service");
  }

  // ---- command handler ----

  public Effect<AdvisorResponse> ask(Question question) {
    log.info("Advisor ask: customer={} message={}", question.customerId(), question.message());
    var userMessage = "Customer " + question.customerId() + " asks: " + question.message();
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(userMessage)
        .responseConformsTo(AdvisorResponse.class)
        .onFailure(err -> {
          log.warn("Advisor response failed", err);
          return new AdvisorResponse(
              "Sorry, I couldn't work that out just now. Let me connect you with a human advisor.",
              new Proposal("NONE", "", "", 0, 0, "", ""),
              true);
        })
        .thenReply();
  }

  // ---- function tools ----

  @FunctionTool(
      description =
          "Returns the customer's recent account activity: per-statement money-in (credits) and "
              + "money-out (debits), plus an estimated average monthly surplus they could save. "
              + "Call this first to ground any savings or affordability advice in real numbers.")
  public String getAccountSummary(@Description("Customer/account id, e.g. acc-1001") String customerId) {
    try {
      var listBody =
          statementClient.GET("/accounts/" + customerId + "/statements").responseBodyAs(String.class).invoke().body();
      JsonNode list = MAPPER.readTree(listBody);
      if (!list.isArray() || list.isEmpty()) {
        return "{\"accountId\":\"" + customerId + "\",\"note\":\"no statements found\"}";
      }

      double totalCredits = 0, totalDebits = 0;
      int n = 0;
      ArrayNode periods = MAPPER.createArrayNode();
      for (JsonNode s : list) {
        String stmtId = s.get("statementId").asText();
        JsonNode full =
            MAPPER.readTree(
                statementClient
                    .GET("/accounts/" + customerId + "/statements/" + stmtId)
                    .responseBodyAs(String.class)
                    .invoke()
                    .body());
        double credits = full.path("totalCredits").asDouble();
        double debits = full.path("totalDebits").asDouble();
        totalCredits += credits;
        totalDebits += debits;
        n++;
        ObjectNode p = MAPPER.createObjectNode();
        p.put("period", full.path("periodStart").asText() + " to " + full.path("periodEnd").asText());
        p.put("credits", credits);
        p.put("debits", debits);
        periods.add(p);
      }
      double avgSurplus = n > 0 ? round2((totalCredits - totalDebits) / n) : 0;

      ObjectNode out = MAPPER.createObjectNode();
      out.put("accountId", customerId);
      out.put("statementCount", n);
      out.put("estimatedMonthlySurplus", avgSurplus);
      out.set("periods", periods);
      return MAPPER.writeValueAsString(out);
    } catch (Exception e) {
      log.warn("getAccountSummary failed for {}", customerId, e);
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }

  @FunctionTool(
      description =
          "Returns the customer's spending analysis: total spend and breakdown by category. "
              + "Use it to understand where their money goes.")
  public String getSpendingProfile(@Description("Customer/account id, e.g. acc-1001") String customerId) {
    try {
      return analysisClient
          .GET("/accounts/" + customerId + "/analysis/summary")
          .responseBodyAs(String.class)
          .invoke()
          .body();
    } catch (Exception e) {
      log.warn("getSpendingProfile failed for {}", customerId, e);
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }

  @FunctionTool(
      description =
          "Lists products in the catalog (savings, takaful, cards, wealth). Optionally filter by "
              + "type/category. Use to find a suitable product to propose.")
  public String listProducts(
      @Description("Optional type/category filter, e.g. 'Savings' or 'Takaful'; empty for all") String type) {
    try {
      String all = productClient.GET("/products").responseBodyAs(String.class).invoke().body();
      if (type == null || type.isBlank()) {
        return all;
      }
      JsonNode arr = MAPPER.readTree(all);
      ArrayNode filtered = MAPPER.createArrayNode();
      String needle = type.toLowerCase();
      for (JsonNode p : arr) {
        String hay = (p.path("category").asText("") + " " + p.path("productName").asText("")).toLowerCase();
        if (hay.contains(needle)) {
          filtered.add(p);
        }
      }
      return MAPPER.writeValueAsString(filtered.isEmpty() ? arr : filtered);
    } catch (Exception e) {
      log.warn("listProducts failed", e);
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }

  @FunctionTool(
      description =
          "Returns the customer's existing savings goals (tabung). Currently returns none — the "
              + "goals service is not built yet.")
  public String getGoals(@Description("Customer/account id") String customerId) {
    return "{\"customerId\":\"" + customerId + "\",\"goals\":[],\"note\":\"No savings goals on file yet.\"}";
  }

  @FunctionTool(
      description =
          "Projects whether a savings target is achievable. Given a target amount, number of months, "
              + "and the monthly surplus available (use estimatedMonthlySurplus from getAccountSummary), "
              + "returns whether it's feasible, the required monthly saving, and any shortfall.")
  public AffordabilityProjection projectAffordability(
      @Description("Target amount to save, e.g. 45000") double targetAmount,
      @Description("Number of months to save over, e.g. 24") int months,
      @Description("Monthly surplus available to save") double monthlySurplus) {
    return AffordabilityProjection.project(targetAmount, months, monthlySurplus);
  }

  @FunctionTool(
      description =
          "Logs a request for a human advisor to follow up with the customer about a topic. Call this "
              + "only when the customer wants to proceed with a consequential action (open a product or "
              + "move money). Returns a confirmation; an advisor will follow up.")
  public String requestHumanHandoff(
      @Description("Customer/account id") String customerId,
      @Description("Topic, e.g. 'open a Hajj tabung savings goal'") String topic) {
    log.info("HUMAN HANDOFF requested: customer={} topic={}", customerId, topic);
    return "Logged. A licensed advisor will follow up with " + customerId + " about: " + topic;
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
