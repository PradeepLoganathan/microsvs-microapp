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
import com.microapp.advisor.domain.AdvisorResponse;
import com.microapp.advisor.domain.AdvisorResponse.ProposedAction;
import com.microapp.advisor.domain.AffordabilityProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Conversational wealth advisor (K1) — refactored to return a structured
 * {@link AdvisorResponse} and to keep its advice constrained to MBSB-only data.
 *
 * <p>Guardrails are layered:</p>
 * <ul>
 *   <li>Tool-level — every {@code @FunctionTool} method only reaches into MBSB
 *       services (statement / analysis / product / goals). The agent can't see
 *       a non-MBSB product even if it tried.</li>
 *   <li>System prompt — explicit MBSB-only scope and off-topic redirect rules.</li>
 *   <li>Closed action enum — the agent picks from {@link ProposedAction.Type}
 *       only; no free-form URLs.</li>
 *   <li>Structured response — {@code responseConformsTo(AdvisorResponse.class)}
 *       forces the model output into the schema.</li>
 * </ul>
 *
 * <p>Per-customer session id = customerId so the conversation is multi-turn.</p>
 */
@Component(id = "wealth-advisor")
public class WealthAdvisorAgent extends Agent {

  private static final Logger log = LoggerFactory.getLogger(WealthAdvisorAgent.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String SYSTEM_MESSAGE =
      """
      You are "K", a warm wealth advisor for MBSB Bank, a Malaysian Islamic bank.
      You help MBSB customers save, budget, and choose suitable MBSB products.

      SCOPE — GUARDRAILS
      - You ONLY discuss MBSB products and the customer's MBSB-held data.
      - If asked about non-MBSB products, other banks, crypto, politics, or off-topic
        matters: politely redirect to MBSB banking and offer a sensible next step.
      - Never invent a product. Never invent a number. Use only what the tools return.
      - Lead with secular examples (holiday, emergency fund, house deposit, education).
        Religious savings are valid only if the customer raises them first.

      GROUNDING — call tools BEFORE answering any numeric question
      - getAccountSummary    — balances in/out + estimated monthly surplus
      - getSpendingProfile   — spend by category
      - getGoals             — the customer's existing savings goals (tabungs)
      - listProducts         — MBSB catalogue (filter by type if useful)
      - projectAffordability — is a target reachable given monthly surplus?
      - requestHumanHandoff  — ONLY when the customer wants to proceed with a
        consequential action (open a financing product, move money); never for advice.

      RESPONSE SHAPE — you MUST conform
        {
          "message":     "<your reply in plain language, citing real numbers>",
          "action":      <one ProposedAction or null>,
          "needsHuman":  <true ONLY if you offered or logged a human follow-up>
        }
      ProposedAction.type MUST be one of: TABUNG, CASA, TAKAFUL, ADVISOR_HUMAN, NONE.

      ACTION TYPES — pick exactly one, or set action to null when advising only
      - TABUNG (preferred for "I want to save for X"):
          label:  "Start a <name> Tabung"
          params: {
            "category":     one of HOLIDAY, HOUSE, EMERGENCY, EDUCATION, HAJJ, OTHER,
            "name":         short display name (e.g. "Bali 2028", "Emergency Fund"),
            "targetAmount": RM amount as a string (e.g. "12000"),
            "targetDate":   "YYYY-MM-DD"
          }
      - CASA:    label: "Open a CASA account",    params: {} (no pre-fill)
      - TAKAFUL: label: "Open a Takaful plan",    params: { "planId": "<id from listProducts>" } when known, else {}
      - ADVISOR_HUMAN: pair with needsHuman=true and a call to requestHumanHandoff.
          label: "Have an advisor follow up", params: {}
      - NONE: omit (set action to null) when only advice is needed.

      HOME FINANCING — when the customer explores a home loan / financing:
      - Ground it: call getAccountSummary (monthly surplus) + listProducts (the
        "home_financing_i" product) and give an indicative monthly figure. Never
        invent a rate — cite the product. Recurring rent is a strong positive signal.
      - If they want to proceed, set action ADVISOR_HUMAN with
        label "Apply for Home Financing", params { "product": "home_financing_i" },
        needsHuman=true, and call requestHumanHandoff. (This is the one case where
        financing is appropriate — don't downsell to a Tabung.)

      STYLE — short, friendly, practical. Cite real numbers. Propose at most ONE action.
      Prefer the lightest commitment (a Tabung) over the heaviest (financing).
      """;

  // ---- input ----

  public record Question(String customerId, String message) {}

  // ---- cross-service HTTP clients (all MBSB-bounded) ----

  private final HttpClient statementClient;
  private final HttpClient analysisClient;
  private final HttpClient productClient;
  private final HttpClient goalsClient;

  public WealthAdvisorAgent(HttpClientProvider httpClientProvider) {
    this.statementClient = httpClientProvider.httpClientFor("statement-service");
    this.analysisClient = httpClientProvider.httpClientFor("analysis-service");
    this.productClient = httpClientProvider.httpClientFor("product-service");
    this.goalsClient = httpClientProvider.httpClientFor("goals-service");
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
          return AdvisorResponse.handoff(
              "Sorry, I couldn't work that out just now. Let me connect you with a human advisor.");
        })
        .thenReply();
  }

  // ---- function tools (all MBSB-only) ----

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
          "Lists MBSB products in the catalog (savings, takaful, cards, wealth). Optionally filter "
              + "by type/category. Use to find a suitable MBSB product to propose. The catalog is "
              + "MBSB-only; non-MBSB products are not available.")
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
          "Returns the customer's existing MBSB savings goals (tabungs): id, name, category, target "
              + "and current amount, progress. Use this before proposing a new tabung to avoid "
              + "duplicating an existing one.")
  public String getGoals(@Description("Customer/account id") String customerId) {
    try {
      return goalsClient
          .GET("/customers/" + customerId + "/goals")
          .responseBodyAs(String.class)
          .invoke()
          .body();
    } catch (Exception e) {
      log.warn("getGoals failed for {}", customerId, e);
      return "{\"customerId\":\"" + customerId + "\",\"goals\":[],\"note\":\"goals-service unavailable\"}";
    }
  }

  @FunctionTool(
      description =
          "Projects whether a savings target is achievable. Given a target amount, number of months, "
              + "and the monthly surplus available (use estimatedMonthlySurplus from getAccountSummary), "
              + "returns whether it's feasible, the required monthly saving, and any shortfall.")
  public AffordabilityProjection projectAffordability(
      @Description("Target amount to save, e.g. 12000") double targetAmount,
      @Description("Number of months to save over, e.g. 24") int months,
      @Description("Monthly surplus available to save") double monthlySurplus) {
    return AffordabilityProjection.project(targetAmount, months, monthlySurplus);
  }

  @FunctionTool(
      description =
          "Logs a request for a human advisor to follow up with the customer about a topic. Call this "
              + "ONLY when the customer wants to proceed with a consequential action (open a financing "
              + "product or move money). Pair with action.type=ADVISOR_HUMAN and needsHuman=true in your "
              + "response.")
  public String requestHumanHandoff(
      @Description("Customer/account id") String customerId,
      @Description("Topic, e.g. 'open a holiday tabung'") String topic) {
    log.info("HUMAN HANDOFF requested: customer={} topic={}", customerId, topic);
    return "Logged. A licensed advisor will follow up with " + customerId + " about: " + topic;
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
