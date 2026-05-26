package com.microapp.customer.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.microapp.customer.domain.CustomerEvent;
import com.microapp.customer.domain.CustomerEvent.KycCompleted;
import com.microapp.customer.domain.CustomerEvent.Registered;
import com.microapp.customer.domain.CustomerEvent.VisitorCreated;
import java.util.Collection;

/**
 * Projects the customer lifecycle for querying by stage (and total). Stage is stored
 * as a String for stable WHERE filtering. Eventually consistent — mutating endpoints
 * read the entity directly, not this view.
 */
@Component(id = "customers-by-stage")
public class CustomersByStageView extends View {

  public record CustomerRow(
      String customerId, String stage, String channel, String offerCode, String accountId, long updatedAt) {}

  public record Customers(Collection<CustomerRow> customers) {}

  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class CustomerRowUpdater extends TableUpdater<CustomerRow> {

    public Effect<CustomerRow> onEvent(CustomerEvent event) {
      // Akka view columns are non-optional Strings — never store null, use "" placeholders.
      return switch (event) {
        case VisitorCreated e ->
            effects().updateRow(new CustomerRow(e.customerId(), "VISITOR", nz(e.channel()), "", "", e.at()));
        case Registered e -> {
          var cur = rowState();
          yield effects().updateRow(new CustomerRow(
              cur.customerId(), "REGISTERED", nz(e.channel()),
              e.offer() != null ? e.offer().code() : "", nz(e.accountId()), e.at()));
        }
        case KycCompleted e -> {
          var cur = rowState();
          yield effects().updateRow(new CustomerRow(
              cur.customerId(), "CUSTOMER", cur.channel(), cur.offerCode(), cur.accountId(), e.at()));
        }
      };
    }

    private static String nz(String s) {
      return s == null ? "" : s;
    }
  }

  @Query("SELECT * AS customers FROM customers_by_stage WHERE stage = :stage")
  public QueryEffect<Customers> byStage(String stage) {
    return queryResult();
  }

  @Query("SELECT * AS customers FROM customers_by_stage")
  public QueryEffect<Customers> all() {
    return queryResult();
  }
}
