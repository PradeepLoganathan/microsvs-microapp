package com.microapp.goals.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.microapp.goals.application.GoalEntity;
import com.microapp.goals.application.GoalEntity.Create;
import com.microapp.goals.application.GoalsByCustomerView;
import com.microapp.goals.application.GoalsByCustomerView.GoalRow;
import com.microapp.goals.domain.Goal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * HTTP API for savings goals ("tabung"). Mutating routes read the entity
 * (strongly consistent) and return a GoalView; list routes read the view
 * (eventually consistent).
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class GoalEndpoint {

  private final ComponentClient componentClient;

  public GoalEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record CreateGoalRequest(String name, String category, double targetAmount, String targetDate) {}

  public record ContributeRequest(double amount) {}

  public record GoalView(
      String goalId,
      String customerId,
      String name,
      String category,
      String status,
      double targetAmount,
      double currentAmount,
      String targetDate,
      double progress,
      double remaining) {}

  public record GoalListView(List<GoalView> goals) {}

  @Post("/customers/{customerId}/goals")
  public GoalView create(String customerId, CreateGoalRequest req) {
    var goalId = "goal-" + UUID.randomUUID();
    var cmd = new Create(customerId, req.name(), parseCategory(req.category()), req.targetAmount(), req.targetDate());
    componentClient.forEventSourcedEntity(goalId).method(GoalEntity::create).invoke(cmd);
    return status(goalId);
  }

  @Post("/goals/{goalId}/contribute")
  public GoalView contribute(String goalId, ContributeRequest req) {
    componentClient.forEventSourcedEntity(goalId).method(GoalEntity::contribute).invoke(req.amount());
    return status(goalId);
  }

  @Post("/goals/{goalId}/complete")
  public GoalView complete(String goalId) {
    componentClient.forEventSourcedEntity(goalId).method(GoalEntity::complete).invoke();
    return status(goalId);
  }

  @Post("/goals/{goalId}/abandon")
  public GoalView abandon(String goalId) {
    componentClient.forEventSourcedEntity(goalId).method(GoalEntity::abandon).invoke();
    return status(goalId);
  }

  @Get("/goals/{goalId}")
  public GoalView get(String goalId) {
    return status(goalId);
  }

  @Get("/customers/{customerId}/goals")
  public GoalListView byCustomer(String customerId) {
    var goals = componentClient.forView().method(GoalsByCustomerView::byCustomer).invoke(customerId).goals();
    return new GoalListView(goals.stream().map(GoalEndpoint::toApi).toList());
  }

  @Get("/customers/{customerId}/goals/active")
  public GoalListView activeByCustomer(String customerId) {
    var goals = componentClient.forView().method(GoalsByCustomerView::activeByCustomer).invoke(customerId).goals();
    return new GoalListView(goals.stream().map(GoalEndpoint::toApi).toList());
  }

  private GoalView status(String goalId) {
    var g = componentClient.forEventSourcedEntity(goalId).method(GoalEntity::get).invoke();
    return toApi(g);
  }

  private static GoalView toApi(Goal g) {
    return new GoalView(
        g.goalId(), g.customerId(), g.name(), g.category().name(), g.status().name(),
        g.targetAmount(), g.currentAmount(), g.targetDate(), g.progress(), g.remaining());
  }

  private static GoalView toApi(GoalRow r) {
    double progress = r.targetAmount() <= 0 ? 0.0
        : Math.min(1.0, r.currentAmount() / r.targetAmount());
    double remaining = Math.max(0.0, r.targetAmount() - r.currentAmount());
    return new GoalView(
        r.goalId(), r.customerId(), r.name(), r.category(), r.status(),
        r.targetAmount(), r.currentAmount(), r.targetDate(), progress, remaining);
  }

  private static Goal.Category parseCategory(String s) {
    if (s == null || s.isBlank()) {
      throw new IllegalArgumentException("category is required");
    }
    try {
      return Goal.Category.valueOf(s.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "invalid category '" + s + "', expected one of: HAJJ, HOLIDAY, HOUSE, EMERGENCY, EDUCATION, OTHER");
    }
  }
}
