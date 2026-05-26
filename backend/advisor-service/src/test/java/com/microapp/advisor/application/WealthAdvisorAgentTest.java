package com.microapp.advisor.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.microapp.advisor.application.WealthAdvisorAgent.AdvisorResponse;
import com.microapp.advisor.application.WealthAdvisorAgent.Proposal;
import com.microapp.advisor.application.WealthAdvisorAgent.Question;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the WealthAdvisorAgent. The LLM is mocked with TestModelProvider
 * (fixedResponse), so no network / OpenAI key / sibling services are needed. With a
 * fixed final response the agent does not request tool calls, keeping the test isolated.
 */
public class WealthAdvisorAgentTest extends TestKitSupport {

  private final TestModelProvider advisorModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(WealthAdvisorAgent.class, advisorModel);
  }

  @Test
  public void returnsStructuredProposal() {
    var expected =
        new AdvisorResponse(
            "At RM 1,900/month you'd reach RM 45,600 in 24 months — that's achievable.",
            new Proposal(
                "CREATE_TABUNG_GOAL",
                "Hajj Tabung",
                "Open a tabung goal to save for Hajj over 2 years.",
                45600.0,
                1900.0,
                "",
                "Based on your estimated ~RM 1,900/month surplus."),
            true);
    advisorModel.fixedResponse(JsonSupport.encodeToString(expected));

    var result =
        componentClient
            .forAgent()
            .inSession("test-session-1")
            .method(WealthAdvisorAgent::ask)
            .invoke(new Question("acc-1001", "Can I afford to save for Hajj in 2 years?"));

    assertThat(result).isEqualTo(expected);
    assertThat(result.proposal().actionType()).isEqualTo("CREATE_TABUNG_GOAL");
    assertThat(result.humanHandoffOffered()).isTrue();
  }

  @Test
  public void fallsBackToHumanHandoffOnBadModelOutput() {
    advisorModel.fixedResponse("this is not valid json");

    var result =
        componentClient
            .forAgent()
            .inSession("test-session-2")
            .method(WealthAdvisorAgent::ask)
            .invoke(new Question("acc-1001", "hello"));

    assertThat(result.humanHandoffOffered()).isTrue();
    assertThat(result.proposal().actionType()).isEqualTo("NONE");
    assertThat(result.reply()).containsIgnoringCase("advisor");
  }
}
