package com.microapp.advisor.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.microapp.advisor.application.WealthAdvisorAgent;
import com.microapp.advisor.domain.AdvisorResponse;
import com.microapp.advisor.domain.AdvisorResponse.ProposedAction;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * HTTP contract test for the advisor endpoint. The LLM is mocked with
 * TestModelProvider (fixed final response → no tool calls / sibling services /
 * OpenAI key needed), so this exercises the endpoint -> agent -> toApi path.
 */
public class AdvisorEndpointIntegrationTest extends TestKitSupport {

  private final TestModelProvider advisorModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(WealthAdvisorAgent.class, advisorModel);
  }

  @Test
  public void postAskReturnsTabungActionOverHttp() {
    var agentReply =
        new AdvisorResponse(
            "Your monthly surplus is around RM 5,800. A holiday fund of RM 12,000 over 24 months "
                + "needs RM 500/month — well within budget.",
            new ProposedAction(
                ProposedAction.Type.TABUNG,
                "Start a Bali Tabung",
                Map.of(
                    "category", "HOLIDAY",
                    "name", "Bali 2028",
                    "targetAmount", "12000",
                    "targetDate", "2028-06-01")),
            false);
    advisorModel.fixedResponse(JsonSupport.encodeToString(agentReply));

    var response =
        httpClient
            .POST("/advisor/acc-1001/ask")
            .withRequestBody(new AdvisorEndpoint.AskRequest("Can I afford a holiday in 2 years?"))
            .responseBodyAs(AdvisorEndpoint.AskResponse.class)
            .invoke();

    assertThat(response.status().isSuccess()).isTrue();
    var body = response.body();
    assertThat(body.message()).contains("RM 5,800");
    assertThat(body.action()).isNotNull();
    assertThat(body.action().type()).isEqualTo("TABUNG"); // stringified for JSON
    assertThat(body.action().label()).isEqualTo("Start a Bali Tabung");
    assertThat(body.action().params())
        .containsEntry("category", "HOLIDAY")
        .containsEntry("name", "Bali 2028")
        .containsEntry("targetAmount", "12000");
    assertThat(body.needsHuman()).isFalse();
  }

  @Test
  public void postAskOmitsActionWhenAdviceOnly() {
    var agentReply =
        new AdvisorResponse(
            "Happy to help — what would you like to plan for?",
            null,
            false);
    advisorModel.fixedResponse(JsonSupport.encodeToString(agentReply));

    var response =
        httpClient
            .POST("/advisor/acc-1001/ask")
            .withRequestBody(new AdvisorEndpoint.AskRequest("hello"))
            .responseBodyAs(AdvisorEndpoint.AskResponse.class)
            .invoke();

    assertThat(response.status().isSuccess()).isTrue();
    var body = response.body();
    assertThat(body.action()).isNull();
    assertThat(body.needsHuman()).isFalse();
    assertThat(body.message()).isNotBlank();
  }

  @Test
  public void postAskDropsNoneTypedActionToNull() {
    // The agent shouldn't normally pick NONE-with-a-label, but if it does, the endpoint
    // normalises it away — the client gets a clean null instead of a button labelled "—".
    var agentReply =
        new AdvisorResponse(
            "Nothing specific to do right now.",
            new ProposedAction(ProposedAction.Type.NONE, "Ignore me", Map.of()),
            false);
    advisorModel.fixedResponse(JsonSupport.encodeToString(agentReply));

    var response =
        httpClient
            .POST("/advisor/acc-1001/ask")
            .withRequestBody(new AdvisorEndpoint.AskRequest("anything else?"))
            .responseBodyAs(AdvisorEndpoint.AskResponse.class)
            .invoke();

    assertThat(response.body().action()).isNull();
  }

  @Test
  public void postAskOffersHumanHandoffForConsequentialActions() {
    var agentReply =
        new AdvisorResponse(
            "Personal financing is a regulated product — a licensed advisor will follow up with you.",
            ProposedAction.humanHandoff("Have an advisor follow up"),
            true);
    advisorModel.fixedResponse(JsonSupport.encodeToString(agentReply));

    var response =
        httpClient
            .POST("/advisor/acc-1001/ask")
            .withRequestBody(new AdvisorEndpoint.AskRequest("I'd like personal financing"))
            .responseBodyAs(AdvisorEndpoint.AskResponse.class)
            .invoke();

    var body = response.body();
    assertThat(body.action()).isNotNull();
    assertThat(body.action().type()).isEqualTo("ADVISOR_HUMAN");
    assertThat(body.needsHuman()).isTrue();
  }
}
