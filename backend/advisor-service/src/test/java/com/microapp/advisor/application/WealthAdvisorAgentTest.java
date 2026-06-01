package com.microapp.advisor.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.microapp.advisor.application.WealthAdvisorAgent.Question;
import com.microapp.advisor.domain.AdvisorResponse;
import com.microapp.advisor.domain.AdvisorResponse.ProposedAction;
import java.util.Map;
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
  public void returnsStructuredTabungAction() {
    var expected =
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
    advisorModel.fixedResponse(JsonSupport.encodeToString(expected));

    var result =
        componentClient
            .forAgent()
            .inSession("test-tabung")
            .method(WealthAdvisorAgent::ask)
            .invoke(new Question("acc-1001", "Can I afford to save for a holiday in 2 years?"));

    assertThat(result).isEqualTo(expected);
    assertThat(result.action()).isNotNull();
    assertThat(result.action().type()).isEqualTo(ProposedAction.Type.TABUNG);
    assertThat(result.action().params()).containsEntry("category", "HOLIDAY");
    assertThat(result.needsHuman()).isFalse();
  }

  @Test
  public void returnsCasaActionWithEmptyParams() {
    var expected =
        new AdvisorResponse(
            "You'll need an MBSB current/savings account before we can set up a savings goal. "
                + "Want to open one now?",
            ProposedAction.casa("Open a CASA account"),
            false);
    advisorModel.fixedResponse(JsonSupport.encodeToString(expected));

    var result =
        componentClient
            .forAgent()
            .inSession("test-casa")
            .method(WealthAdvisorAgent::ask)
            .invoke(new Question("visitor-1", "I'm new — where do I start?"));

    assertThat(result.action()).isNotNull();
    assertThat(result.action().type()).isEqualTo(ProposedAction.Type.CASA);
    assertThat(result.action().label()).isEqualTo("Open a CASA account");
    assertThat(result.action().params()).isEmpty();
  }

  @Test
  public void returnsAdviceOnlyWhenNoActionFits() {
    var expected =
        new AdvisorResponse(
            "Your spending is well within your income — about RM 5,800/month surplus on average. "
                + "Keep it up.",
            null,
            false);
    advisorModel.fixedResponse(JsonSupport.encodeToString(expected));

    var result =
        componentClient
            .forAgent()
            .inSession("test-no-action")
            .method(WealthAdvisorAgent::ask)
            .invoke(new Question("acc-1001", "How is my spending this month?"));

    assertThat(result.action()).isNull();
    assertThat(result.needsHuman()).isFalse();
    assertThat(result.message()).contains("surplus");
  }

  @Test
  public void offersHumanHandoffForConsequentialActions() {
    var expected =
        new AdvisorResponse(
            "Personal financing is a regulated product — a licensed advisor will follow up with you.",
            ProposedAction.humanHandoff("Have an advisor follow up"),
            true);
    advisorModel.fixedResponse(JsonSupport.encodeToString(expected));

    var result =
        componentClient
            .forAgent()
            .inSession("test-handoff")
            .method(WealthAdvisorAgent::ask)
            .invoke(new Question("acc-1001", "I'd like to take out a personal financing loan"));

    assertThat(result.action()).isNotNull();
    assertThat(result.action().type()).isEqualTo(ProposedAction.Type.ADVISOR_HUMAN);
    assertThat(result.needsHuman()).isTrue();
  }

  @Test
  public void fallsBackToHandoffOnInvalidModelOutput() {
    // The agent's responseConformsTo cannot parse this — onFailure should kick in.
    advisorModel.fixedResponse("this is not valid json at all");

    var result =
        componentClient
            .forAgent()
            .inSession("test-bad-output")
            .method(WealthAdvisorAgent::ask)
            .invoke(new Question("acc-1001", "hello"));

    assertThat(result.needsHuman()).isTrue();
    assertThat(result.action()).isNull();
    assertThat(result.message()).containsIgnoringCase("advisor");
  }
}
