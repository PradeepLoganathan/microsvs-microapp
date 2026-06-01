package com.microapp.goals.domain;

import akka.javasdk.annotations.TypeName;

/** Events for a savings goal. ContributionMade carries the resulting
 *  balance so views can index progress without recomputation. */
public sealed interface GoalEvent {

  @TypeName("goal-created")
  record GoalCreated(
      String goalId,
      String customerId,
      String name,
      Goal.Category category,
      double targetAmount,
      String targetDate,
      long at)
      implements GoalEvent {}

  @TypeName("contribution-made")
  record ContributionMade(double amount, double newCurrentAmount, long at) implements GoalEvent {}

  @TypeName("goal-completed")
  record GoalCompleted(long at) implements GoalEvent {}

  @TypeName("goal-abandoned")
  record GoalAbandoned(long at) implements GoalEvent {}
}
