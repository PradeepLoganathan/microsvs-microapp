package com.microapp.goals.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import com.microapp.goals.api.GoalEndpoint.ContributeRequest;
import com.microapp.goals.api.GoalEndpoint.CreateGoalRequest;
import com.microapp.goals.api.GoalEndpoint.GoalListView;
import com.microapp.goals.api.GoalEndpoint.GoalView;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class GoalEndpointIntegrationTest extends TestKitSupport {

  @Test
  public void createContributeAndCompleteOverHttp() {
    var customerId = "acc-" + UUID.randomUUID();

    var created = httpClient.POST("/customers/" + customerId + "/goals")
        .withRequestBody(new CreateGoalRequest("Hajj 2028", "HAJJ", 5_000.0, "2028-06-01"))
        .responseBodyAs(GoalView.class).invoke();
    assertThat(created.status().isSuccess()).isTrue();
    var goalId = created.body().goalId();
    assertThat(goalId).startsWith("goal-");
    assertThat(created.body().status()).isEqualTo("ACTIVE");
    assertThat(created.body().currentAmount()).isZero();
    assertThat(created.body().progress()).isZero();

    var partial = httpClient.POST("/goals/" + goalId + "/contribute")
        .withRequestBody(new ContributeRequest(1_500.0))
        .responseBodyAs(GoalView.class).invoke();
    assertThat(partial.body().currentAmount()).isEqualTo(1_500.0);
    assertThat(partial.body().status()).isEqualTo("ACTIVE");
    assertThat(partial.body().progress()).isEqualTo(0.3);

    var done = httpClient.POST("/goals/" + goalId + "/contribute")
        .withRequestBody(new ContributeRequest(4_000.0))
        .responseBodyAs(GoalView.class).invoke();
    assertThat(done.body().currentAmount()).isEqualTo(5_500.0);
    assertThat(done.body().status()).isEqualTo("COMPLETED");
    assertThat(done.body().progress()).isEqualTo(1.0);
    assertThat(done.body().remaining()).isZero();
  }

  @Test
  public void listByCustomerSurfacesNewGoals() {
    var customerId = "acc-" + UUID.randomUUID();

    httpClient.POST("/customers/" + customerId + "/goals")
        .withRequestBody(new CreateGoalRequest("Holiday Bali", "HOLIDAY", 3_000.0, "2027-04-01"))
        .responseBodyAs(GoalView.class).invoke();
    httpClient.POST("/customers/" + customerId + "/goals")
        .withRequestBody(new CreateGoalRequest("Emergency Fund", "EMERGENCY", 10_000.0, "2027-12-01"))
        .responseBodyAs(GoalView.class).invoke();

    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      var list = httpClient.GET("/customers/" + customerId + "/goals")
          .responseBodyAs(GoalListView.class).invoke().body();
      assertThat(list.goals()).hasSize(2);
      assertThat(list.goals()).extracting(GoalView::name)
          .containsExactlyInAnyOrder("Holiday Bali", "Emergency Fund");
    });
  }

  @Test
  public void abandonedGoalDropsFromActiveList() {
    var customerId = "acc-" + UUID.randomUUID();

    var keep = httpClient.POST("/customers/" + customerId + "/goals")
        .withRequestBody(new CreateGoalRequest("Keep this", "OTHER", 1_000.0, "2027-01-01"))
        .responseBodyAs(GoalView.class).invoke().body();
    var drop = httpClient.POST("/customers/" + customerId + "/goals")
        .withRequestBody(new CreateGoalRequest("Drop this", "OTHER", 500.0, "2027-01-01"))
        .responseBodyAs(GoalView.class).invoke().body();

    httpClient.POST("/goals/" + drop.goalId() + "/abandon").invoke();

    Awaitility.await().ignoreExceptions().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      var active = httpClient.GET("/customers/" + customerId + "/goals/active")
          .responseBodyAs(GoalListView.class).invoke().body();
      assertThat(active.goals()).extracting(GoalView::goalId).containsExactly(keep.goalId());
    });
  }

  @Test
  public void rejectsInvalidCategory() {
    var customerId = "acc-" + UUID.randomUUID();
    var resp = httpClient.POST("/customers/" + customerId + "/goals")
        .withRequestBody(new CreateGoalRequest("Bad", "NOT_A_CATEGORY", 100.0, "2027-01-01"))
        .invoke();
    assertThat(resp.status().isSuccess()).isFalse();
  }
}
