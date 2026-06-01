package com.microapp.advisor.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.microapp.advisor.application.WealthAdvisorAgent;
import com.microapp.advisor.domain.AdvisorResponse;
import com.microapp.advisor.domain.AdvisorResponse.ProposedAction;
import java.util.Map;

/**
 * HTTP API for the wealth advisor.
 *
 * <p>POST /advisor/{customerId}/ask — ask the advisor a question; the session id
 * is the customerId so the conversation is continuous per customer (multi-turn
 * memory).</p>
 *
 * <p>Returns an API-specific {@link AskResponse} mirroring the agent's
 * {@link AdvisorResponse} shape — the {@link ProposedAction.Type} enum is
 * stringified for JSON friendliness, and a NONE-typed action is dropped to null
 * so the client can simply {@code if (response.action)} render its button.</p>
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/advisor")
public class AdvisorEndpoint extends AbstractHttpEndpoint {

  private final ComponentClient componentClient;

  public AdvisorEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record AskRequest(String message) {}

  public record ActionView(String type, String label, Map<String, String> params) {}

  public record AskResponse(String message, ActionView action, boolean needsHuman) {}

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

  private static AskResponse toApi(AdvisorResponse r) {
    ProposedAction a = r.action();
    ActionView view =
        (a == null || a.type() == null || a.type().isBlank() || ProposedAction.Type.NONE.equals(a.type()))
            ? null
            : new ActionView(
                a.type(),
                a.label() == null ? "" : a.label(),
                a.params() == null ? Map.of() : a.params());
    return new AskResponse(r.message(), view, r.needsHuman());
  }
}
