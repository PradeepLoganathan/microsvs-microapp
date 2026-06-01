package com.microapp.goals.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.microapp.goals.application.GoalEntity.Create;
import com.microapp.goals.domain.Goal;
import com.microapp.goals.domain.Goal.Category;
import com.microapp.goals.domain.Goal.Status;
import com.microapp.goals.domain.GoalEvent.ContributionMade;
import com.microapp.goals.domain.GoalEvent.GoalCompleted;
import com.microapp.goals.domain.GoalEvent.GoalCreated;
import org.junit.jupiter.api.Test;

public class GoalEntityTest {

  private static Create sampleSpec() {
    return new Create("acc-1001", "Hajj 2028", Category.HAJJ, 10_000.0, "2028-06-01");
  }

  @Test
  public void createGoalHappyPath() {
    var testKit = EventSourcedTestKit.of("goal-1", GoalEntity::new);

    var r = testKit.method(GoalEntity::create).invoke(sampleSpec());
    assertThat(r.isReply()).isTrue();

    GoalCreated ev = r.getNextEventOfType(GoalCreated.class);
    assertThat(ev.goalId()).isEqualTo("goal-1");
    assertThat(ev.customerId()).isEqualTo("acc-1001");

    Goal state = testKit.getState();
    assertThat(state.name()).isEqualTo("Hajj 2028");
    assertThat(state.category()).isEqualTo(Category.HAJJ);
    assertThat(state.targetAmount()).isEqualTo(10_000.0);
    assertThat(state.currentAmount()).isZero();
    assertThat(state.status()).isEqualTo(Status.ACTIVE);
  }

  @Test
  public void createRejectsDuplicate() {
    var testKit = EventSourcedTestKit.of("goal-2", GoalEntity::new);
    testKit.method(GoalEntity::create).invoke(sampleSpec());

    var r = testKit.method(GoalEntity::create).invoke(sampleSpec());
    assertThat(r.isError()).isTrue();
    assertThat(r.getError()).contains("already exists");
  }

  @Test
  public void createValidatesInput() {
    var testKit = EventSourcedTestKit.of("goal-3", GoalEntity::new);
    var bad = new Create("acc-1001", "", Category.OTHER, 0.0, "2028-01-01");

    var r = testKit.method(GoalEntity::create).invoke(bad);
    assertThat(r.isError()).isTrue();
    assertThat(r.getError()).containsIgnoringCase("invalid goal");
  }

  @Test
  public void contributeIncreasesCurrentAmount() {
    var testKit = EventSourcedTestKit.of("goal-4", GoalEntity::new);
    testKit.method(GoalEntity::create).invoke(sampleSpec());

    var r = testKit.method(GoalEntity::contribute).invoke(2_500.0);
    assertThat(r.isReply()).isTrue();

    ContributionMade ev = r.getNextEventOfType(ContributionMade.class);
    assertThat(ev.amount()).isEqualTo(2_500.0);
    assertThat(ev.newCurrentAmount()).isEqualTo(2_500.0);

    Goal state = testKit.getState();
    assertThat(state.currentAmount()).isEqualTo(2_500.0);
    assertThat(state.status()).isEqualTo(Status.ACTIVE);
    assertThat(state.progress()).isEqualTo(0.25);
  }

  @Test
  public void contributePastTargetAutoCompletes() {
    var testKit = EventSourcedTestKit.of("goal-5", GoalEntity::new);
    testKit.method(GoalEntity::create).invoke(sampleSpec());

    var r = testKit.method(GoalEntity::contribute).invoke(10_500.0);
    assertThat(r.isReply()).isTrue();
    assertThat(r.getAllEvents()).hasSize(2);
    assertThat(r.getNextEventOfType(ContributionMade.class).amount()).isEqualTo(10_500.0);
    assertThat(r.getNextEventOfType(GoalCompleted.class)).isNotNull();

    Goal state = testKit.getState();
    assertThat(state.currentAmount()).isEqualTo(10_500.0);
    assertThat(state.status()).isEqualTo(Status.COMPLETED);
    assertThat(state.progress()).isEqualTo(1.0); // capped
    assertThat(state.remaining()).isZero();
  }

  @Test
  public void contributeRejectsNonPositiveAmount() {
    var testKit = EventSourcedTestKit.of("goal-6", GoalEntity::new);
    testKit.method(GoalEntity::create).invoke(sampleSpec());

    var r = testKit.method(GoalEntity::contribute).invoke(0.0);
    assertThat(r.isError()).isTrue();
    assertThat(r.getError()).containsIgnoringCase("positive");
  }

  @Test
  public void contributeRequiresActiveStatus() {
    var testKit = EventSourcedTestKit.of("goal-7", GoalEntity::new);
    testKit.method(GoalEntity::create).invoke(sampleSpec());
    testKit.method(GoalEntity::complete).invoke();

    var r = testKit.method(GoalEntity::contribute).invoke(100.0);
    assertThat(r.isError()).isTrue();
    assertThat(r.getError()).containsIgnoringCase("active");
  }

  @Test
  public void abandonMovesToAbandoned() {
    var testKit = EventSourcedTestKit.of("goal-8", GoalEntity::new);
    testKit.method(GoalEntity::create).invoke(sampleSpec());

    var r = testKit.method(GoalEntity::abandon).invoke();
    assertThat(r.isReply()).isTrue();
    assertThat(testKit.getState().status()).isEqualTo(Status.ABANDONED);
  }

  @Test
  public void getErrorsBeforeCreate() {
    var testKit = EventSourcedTestKit.of("goal-9", GoalEntity::new);
    var r = testKit.method(GoalEntity::get).invoke();
    assertThat(r.isError()).isTrue();
    assertThat(r.getError()).contains("no goal found");
  }
}
