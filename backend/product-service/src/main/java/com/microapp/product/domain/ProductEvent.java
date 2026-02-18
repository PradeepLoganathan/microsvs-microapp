package com.microapp.product.domain;

import akka.javasdk.annotations.TypeName;

public sealed interface ProductEvent {

  @TypeName("product-created")
  record ProductCreated(
      String productId,
      String productName,
      String category,
      String description,
      String eligibility
  ) implements ProductEvent {}

  @TypeName("product-updated")
  record ProductUpdated(
      String productName,
      String category,
      String description,
      String eligibility
  ) implements ProductEvent {}

  @TypeName("product-deleted")
  record ProductDeleted() implements ProductEvent {}
}
