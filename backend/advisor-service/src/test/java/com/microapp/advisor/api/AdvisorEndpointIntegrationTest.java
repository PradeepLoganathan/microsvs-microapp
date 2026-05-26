package com.microapp.advisor.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.microapp.advisor.application.WealthAdvisorAgent;
import com.microapp.advisor.application.WealthAdvisorAgent.AdvisorResponse;
import com.microapp.advisor.application.WealthAdvisorAgent.Proposal;
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
  public void postAskReturnsProposalOverHttp() {
    var agentReply =
        new AdvisorResponse(
            "At RM 1,900/month you'd reach RM 45,600 in 24 months — achievable.",
            new Proposal(
                "CREATE_TABUNG_GOAL",
                "Hajj Tabung",
                "Open a tabung goal to save for Hajj over 2 years.",
                45600.0,
                1900.0,
                "",
                "Based on your ~RM 1,900/month surplus."),
            true);
    advisorModel.fixedResponse(JsonSupport.encodeToString(agentReply));

    var response =
        httpClient
            .POST("/advisor/acc-1001/ask")
            .withRequestBody(new AdvisorEndpoint.AskRequest("Can I afford to save for Hajj in 2 years?"))
            .responseBodyAs(AdvisorEndpoint.AskResponse.class)
            .invoke();

    assertThat(response.status().isSuccess()).isTrue();
    var body = response.body();
    assertThat(body.humanHandoffOffered()).isTrue();
    assertThat(body.proposal()).isNotNull();
    assertThat(body.proposal().actionType()).isEqualTo("CREATE_TABUNG_GOAL");
    assertThat(body.proposal().monthlyContribution()).isEqualTo(1900.0);
  }

  @Test
  public void postAskOmitsProposalWhenNone() {
    var agentReply =
        new AdvisorResponse(
            "Happy to help — what would you like to plan for?",
            new Proposal("NONE", "", "", 0, 0, "", ""),
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
    assertThat(body.proposal()).isNull(); // NONE is mapped to no proposal
    assertThat(body.humanHandoffOffered()).isFalse();
    assertThat(body.reply()).isNotBlank();
  }
}
