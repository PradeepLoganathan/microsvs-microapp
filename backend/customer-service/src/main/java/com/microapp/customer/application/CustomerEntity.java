package com.microapp.customer.application;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.microapp.customer.domain.Customer;
import com.microapp.customer.domain.Customer.Registration;
import com.microapp.customer.domain.Customer.Stage;
import com.microapp.customer.domain.CustomerEvent;
import com.microapp.customer.domain.CustomerEvent.KycCompleted;
import com.microapp.customer.domain.CustomerEvent.Registered;
import com.microapp.customer.domain.CustomerEvent.VisitorCreated;
import com.microapp.customer.domain.WelcomeOfferRules;
import java.time.Instant;
import java.util.UUID;

/**
 * The customer lifecycle as a single event-sourced entity that evolves through
 * VISITOR → REGISTERED → CUSTOMER. The same entity id is used at every stage
 * (no migration) — that's the event-sourcing proof for the RFI.
 */
@Component(id = "customer")
public class CustomerEntity extends EventSourcedEntity<Customer, CustomerEvent> {

  /** eKYC command payload (stubbed verification). */
  public record EkycRequest(String idType, String idNumber, boolean consent) {}

  public Effect<Done> createVisitor(String channel) {
    if (currentState() != null) {
      return effects().reply(done()); // idempotent — cold open may fire twice
    }
    return effects()
        .persist(new VisitorCreated(commandContext().entityId(), channel, now()))
        .thenReply(__ -> done());
  }

  public Effect<Done> register(Registration registration) {
    if (currentState() == null || currentState().stage() != Stage.VISITOR) {
      return effects().error("can only register a VISITOR");
    }
    if (!registration.isValid()) {
      return effects().error("email and phone are required");
    }
    var offer = WelcomeOfferRules.forRegistration(registration);
    return effects()
        .persist(new Registered(
            registration.email(), registration.phone(), registration.channel(), offer, "acc-1001", now()))
        .thenReply(__ -> done());
  }

  public Effect<Done> completeKyc(EkycRequest req) {
    if (currentState() == null || currentState().stage() != Stage.REGISTERED) {
      return effects().error("can only complete eKYC for a REGISTERED customer");
    }
    if (!req.consent() || req.idNumber() == null || req.idNumber().isBlank()) {
      return effects().error("eKYC requires consent and an id number");
    }
    return effects()
        .persist(new KycCompleted("kyc-" + UUID.randomUUID(), now()))
        .thenReply(__ -> done());
  }

  public ReadOnlyEffect<Customer> get() {
    if (currentState() == null) {
      return effects().error("No customer found for id '" + commandContext().entityId() + "'");
    }
    return effects().reply(currentState());
  }

  @Override
  public Customer applyEvent(CustomerEvent event) {
    return switch (event) {
      case VisitorCreated e -> Customer.visitor(e.customerId(), e.channel(), e.at());
      case Registered e ->
          currentState().registered(e.email(), e.phone(), e.channel(), e.offer(), e.accountId(), e.at());
      case KycCompleted e -> currentState().withKyc(e.kycRef(), e.at());
    };
  }

  private static long now() {
    return Instant.now().toEpochMilli();
  }
}
