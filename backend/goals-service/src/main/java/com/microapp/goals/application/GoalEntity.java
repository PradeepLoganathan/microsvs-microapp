package com.microapp.goals.application;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.microapp.goals.domain.Goal;
import com.microapp.goals.domain.Goal.Spec;
import com.microapp.goals.domain.Goal.Status;
import com.microapp.goals.domain.GoalEvent;
import com.microapp.goals.domain.GoalEvent.ContributionMade;
import com.microapp.goals.domain.GoalEvent.GoalAbandoned;
import com.microapp.goals.domain.GoalEvent.GoalCompleted;
import com.microapp.goals.domain.GoalEvent.GoalCreated;
import java.time.Instant;

/**
 * A single savings goal ("tabung"). Entity id = goalId. Contributing past the
 * target auto-emits GoalCompleted alongside ContributionMade so views see the
 * lifecycle without a separate caller call.
 */
@Component(id = "goal")
public class GoalEntity extends EventSourcedEntity<Goal, GoalEvent> {

  /** Create payload — carries customerId since the entity id is the goalId. */
  public record Create(
      String customerId,
      String name,
      Goal.Category category,
      double targetAmount,
      String targetDate) {

    public Spec toSpec() {
      return new Spec(name, category, targetAmount, targetDate);
    }

    public boolean isValid() {
      return customerId != null && !customerId.isBlank() && toSpec().isValid();
    }
  }

  public Effect<Done> create(Create cmd) {
    if (currentState() != null) {
      return effects().error("goal '" + commandContext().entityId() + "' already exists");
    }
    if (!cmd.isValid()) {
      return effects().error("invalid goal: customerId, name, category, targetAmount>0 and targetDate are required");
    }
    return effects()
        .persist(new GoalCreated(
            commandContext().entityId(),
            cmd.customerId(),
            cmd.name(),
            cmd.category(),
            cmd.targetAmount(),
            cmd.targetDate(),
            now()))
        .thenReply(__ -> done());
  }

  public Effect<Done> contribute(double amount) {
    if (currentState() == null) {
      return effects().error("no goal found for id '" + commandContext().entityId() + "'");
    }
    if (currentState().status() != Status.ACTIVE) {
      return effects().error("can only contribute to an ACTIVE goal");
    }
    if (amount <= 0) {
      return effects().error("contribution amount must be positive");
    }
    long at = now();
    var updated = currentState().contribute(amount, at);
    if (updated.isReached()) {
      return effects()
          .persist(
              new ContributionMade(amount, updated.currentAmount(), at),
              new GoalCompleted(at))
          .thenReply(__ -> done());
    }
    return effects()
        .persist(new ContributionMade(amount, updated.currentAmount(), at))
        .thenReply(__ -> done());
  }

  public Effect<Done> complete() {
    if (currentState() == null) {
      return effects().error("no goal found for id '" + commandContext().entityId() + "'");
    }
    if (currentState().status() != Status.ACTIVE) {
      return effects().error("can only complete an ACTIVE goal");
    }
    return effects().persist(new GoalCompleted(now())).thenReply(__ -> done());
  }

  public Effect<Done> abandon() {
    if (currentState() == null) {
      return effects().error("no goal found for id '" + commandContext().entityId() + "'");
    }
    if (currentState().status() != Status.ACTIVE) {
      return effects().error("can only abandon an ACTIVE goal");
    }
    return effects().persist(new GoalAbandoned(now())).thenReply(__ -> done());
  }

  public ReadOnlyEffect<Goal> get() {
    if (currentState() == null) {
      return effects().error("no goal found for id '" + commandContext().entityId() + "'");
    }
    return effects().reply(currentState());
  }

  @Override
  public Goal applyEvent(GoalEvent event) {
    return switch (event) {
      case GoalCreated e ->
          Goal.create(
              e.goalId(),
              e.customerId(),
              new Spec(e.name(), e.category(), e.targetAmount(), e.targetDate()),
              e.at());
      case ContributionMade e -> currentState().contribute(e.amount(), e.at());
      case GoalCompleted e -> currentState().complete(e.at());
      case GoalAbandoned e -> currentState().abandon(e.at());
    };
  }

  private static long now() {
    return Instant.now().toEpochMilli();
  }
}
