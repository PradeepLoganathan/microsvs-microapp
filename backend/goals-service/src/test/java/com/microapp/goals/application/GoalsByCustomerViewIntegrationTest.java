package com.microapp.goals.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import com.microapp.goals.application.GoalEntity.Create;
import com.microapp.goals.application.GoalsByCustomerView.GoalRow;
import com.microapp.goals.domain.Goal.Category;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class GoalsByCustomerViewIntegrationTest extends TestKitSupport {

  private Collection<GoalRow> byCustomer(String customerId) {
    return componentClient.forView().method(GoalsByCustomerView::byCustomer).invoke(customerId).goals();
  }

  private Collection<GoalRow> activeByCustomer(String customerId) {
    return componentClient
        .forView()
        .method(GoalsByCustomerView::activeByCustomer)
        .invoke(customerId)
        .goals();
  }

  @Test
  public void rowAppearsAndProgressesToCompleted() {
    var customerId = "acc-" + UUID.randomUUID();
    var goalId = "goal-" + UUID.randomUUID();

    componentClient
        .forEventSourcedEntity(goalId)
        .method(GoalEntity::create)
        .invoke(new Create(customerId, "Hajj 2028", Category.HAJJ, 5_000.0, "2028-06-01"));

    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      var row = byCustomer(customerId).stream().filter(r -> r.goalId().equals(goalId)).findFirst();
      assertThat(row).isPresent();
      assertThat(row.get().status()).isEqualTo("ACTIVE");
      assertThat(row.get().targetAmount()).isEqualTo(5_000.0);
      assertThat(row.get().currentAmount()).isZero();
    });

    componentClient.forEventSourcedEntity(goalId).method(GoalEntity::contribute).invoke(1_500.0);
    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      var row = byCustomer(customerId).stream().filter(r -> r.goalId().equals(goalId)).findFirst();
      assertThat(row).isPresent();
      assertThat(row.get().currentAmount()).isEqualTo(1_500.0);
      assertThat(row.get().status()).isEqualTo("ACTIVE");
    });

    componentClient.forEventSourcedEntity(goalId).method(GoalEntity::contribute).invoke(4_000.0);
    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      var row = byCustomer(customerId).stream().filter(r -> r.goalId().equals(goalId)).findFirst();
      assertThat(row).isPresent();
      assertThat(row.get().currentAmount()).isEqualTo(5_500.0);
      assertThat(row.get().status()).isEqualTo("COMPLETED");
    });
  }

  @Test
  public void activeByCustomerExcludesAbandoned() {
    var customerId = "acc-" + UUID.randomUUID();
    var keepId = "goal-" + UUID.randomUUID();
    var dropId = "goal-" + UUID.randomUUID();

    componentClient
        .forEventSourcedEntity(keepId)
        .method(GoalEntity::create)
        .invoke(new Create(customerId, "Holiday Bali", Category.HOLIDAY, 3_000.0, "2027-04-01"));
    componentClient
        .forEventSourcedEntity(dropId)
        .method(GoalEntity::create)
        .invoke(new Create(customerId, "Old idea", Category.OTHER, 1_000.0, "2027-01-01"));
    componentClient.forEventSourcedEntity(dropId).method(GoalEntity::abandon).invoke();

    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      var active = activeByCustomer(customerId);
      assertThat(active).anyMatch(r -> r.goalId().equals(keepId));
      assertThat(active).noneMatch(r -> r.goalId().equals(dropId));

      var all = byCustomer(customerId);
      assertThat(all).anyMatch(r -> r.goalId().equals(dropId) && r.status().equals("ABANDONED"));
    });
  }
}
