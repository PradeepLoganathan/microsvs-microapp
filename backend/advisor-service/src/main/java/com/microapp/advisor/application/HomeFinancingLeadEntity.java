package com.microapp.advisor.application;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.microapp.advisor.domain.HomeFinancingLead;

/** Per-customer home-financing lead (entity id = customerId). */
@Component(id = "home-financing-lead")
public class HomeFinancingLeadEntity extends KeyValueEntity<HomeFinancingLead> {

  private final String customerId;

  public HomeFinancingLeadEntity(KeyValueEntityContext context) {
    this.customerId = context.entityId();
  }

  @Override
  public HomeFinancingLead emptyState() {
    return HomeFinancingLead.none(customerId);
  }

  public record Apply(double amount, String note, String requestedAt) {}

  public Effect<Done> apply(Apply cmd) {
    return effects()
        .updateState(currentState().submitted(cmd.amount(), cmd.note(), cmd.requestedAt()))
        .thenReply(done());
  }

  public ReadOnlyEffect<HomeFinancingLead> get() {
    return effects().reply(currentState());
  }
}
