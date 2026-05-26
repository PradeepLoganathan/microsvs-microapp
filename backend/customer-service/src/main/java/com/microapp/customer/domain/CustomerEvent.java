package com.microapp.customer.domain;

import akka.javasdk.annotations.TypeName;

/** Events for the customer lifecycle. The Registered event carries the welcome offer
 *  so the view can index it without recomputation. */
public sealed interface CustomerEvent {

  @TypeName("visitor-created")
  record VisitorCreated(String customerId, String channel, long at) implements CustomerEvent {}

  @TypeName("registered")
  record Registered(
      String email,
      String phone,
      String channel,
      Customer.WelcomeOffer offer,
      String accountId,
      long at)
      implements CustomerEvent {}

  @TypeName("kyc-completed")
  record KycCompleted(String kycRef, long at) implements CustomerEvent {}
}
