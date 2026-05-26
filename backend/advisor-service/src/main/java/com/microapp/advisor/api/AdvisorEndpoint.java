package com.microapp.advisor.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.microapp.advisor.application.WealthAdvisorAgent;

/**
 * HTTP API for the wealth advisor.
 *
 * POST /advisor/{customerId}/ask  — ask the advisor a question; the session id is
 * the customerId so the conversation is continuous per customer (multi-turn memory).
 *
 * Returns an API-specific {@link AskResponse} (never the agent's own type): the
 * proposal is included only when the agent actually proposed an action.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/advisor")
public class AdvisorEndpoint extends AbstractHttpEndpoint {

  private final ComponentClient componentClient;

  public AdvisorEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record AskRequest(String message) {}

  public record ProposalView(
      String actionType,
      String title,
      String description,
      double targetAmount,
      double monthlyContribution,
      String productId,
      String rationale) {}

  public record AskResponse(String reply, ProposalView proposal, boolean humanHandoffOffered) {}

  @Post("/{customerId}/ask")
  public AskResponse ask(String customerId, AskRequest request) {
    var response =
        componentClient
            .forAgent()
            .inSession(customerId) // per-customer conversation
            .method(WealthAdvisorAgent::ask)
            .invoke(new WealthAdvisorAgent.Question(customerId, request.message()));
    return toApi(response);
  }

  private static AskResponse toApi(WealthAdvisorAgent.AdvisorResponse r) {
    var p = r.proposal();
    ProposalView view =
        (p == null || "NONE".equalsIgnoreCase(p.actionType()))
            ? null
            : new ProposalView(
                p.actionType(),
                p.title(),
                p.description(),
                p.targetAmount(),
                p.monthlyContribution(),
                p.productId(),
                p.rationale());
    return new AskResponse(r.reply(), view, r.humanHandoffOffered());
  }
}
