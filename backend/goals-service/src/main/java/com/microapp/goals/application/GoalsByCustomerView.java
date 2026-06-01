package com.microapp.goals.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.microapp.goals.domain.GoalEvent;
import com.microapp.goals.domain.GoalEvent.ContributionMade;
import com.microapp.goals.domain.GoalEvent.GoalAbandoned;
import com.microapp.goals.domain.GoalEvent.GoalCompleted;
import com.microapp.goals.domain.GoalEvent.GoalCreated;
import java.util.Collection;

/**
 * Projects savings goals indexed by customerId. Eventually consistent —
 * mutating endpoints read the entity directly; this view drives list/dashboard reads.
 */
@Component(id = "goals-by-customer")
public class GoalsByCustomerView extends View {

  public record GoalRow(
      String goalId,
      String customerId,
      String name,
      String category,
      String status,
      double targetAmount,
      double currentAmount,
      String targetDate,
      long updatedAt) {}

  public record Goals(Collection<GoalRow> goals) {}

  @Consume.FromEventSourcedEntity(GoalEntity.class)
  public static class GoalRowUpdater extends TableUpdater<GoalRow> {

    public Effect<GoalRow> onEvent(GoalEvent event) {
      return switch (event) {
        case GoalCreated e ->
            effects().updateRow(new GoalRow(
                e.goalId(),
                e.customerId(),
                e.name(),
                e.category().name(),
                "ACTIVE",
                e.targetAmount(),
                0.0,
                e.targetDate(),
                e.at()));
        case ContributionMade e -> {
          var cur = rowState();
          yield effects().updateRow(new GoalRow(
              cur.goalId(), cur.customerId(), cur.name(), cur.category(), cur.status(),
              cur.targetAmount(), e.newCurrentAmount(), cur.targetDate(), e.at()));
        }
        case GoalCompleted e -> {
          var cur = rowState();
          yield effects().updateRow(new GoalRow(
              cur.goalId(), cur.customerId(), cur.name(), cur.category(), "COMPLETED",
              cur.targetAmount(), cur.currentAmount(), cur.targetDate(), e.at()));
        }
        case GoalAbandoned e -> {
          var cur = rowState();
          yield effects().updateRow(new GoalRow(
              cur.goalId(), cur.customerId(), cur.name(), cur.category(), "ABANDONED",
              cur.targetAmount(), cur.currentAmount(), cur.targetDate(), e.at()));
        }
      };
    }
  }

  @Query("SELECT * AS goals FROM goals_by_customer WHERE customerId = :customerId")
  public QueryEffect<Goals> byCustomer(String customerId) {
    return queryResult();
  }

  @Query("SELECT * AS goals FROM goals_by_customer WHERE customerId = :customerId AND status = 'ACTIVE'")
  public QueryEffect<Goals> activeByCustomer(String customerId) {
    return queryResult();
  }
}
