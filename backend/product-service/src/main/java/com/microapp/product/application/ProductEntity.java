package com.microapp.product.application;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.microapp.product.domain.Product;
import com.microapp.product.domain.ProductEvent;
import com.microapp.product.domain.ProductEvent.ProductCreated;
import com.microapp.product.domain.ProductEvent.ProductUpdated;
import com.microapp.product.domain.ProductEvent.ProductDeleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "product")
public class ProductEntity extends EventSourcedEntity<Product, ProductEvent> {

  private static final Logger logger = LoggerFactory.getLogger(ProductEntity.class);

  private boolean isActive() {
    return currentState() != null && !currentState().deleted();
  }

  public ReadOnlyEffect<Product> getProduct() {
    if (!isActive()) {
      return effects().error("No product found for id '" + commandContext().entityId() + "'");
    }
    return effects().reply(currentState());
  }

  public Effect<Done> create(Product product) {
    if (isActive()) {
      // Already exists — idempotent
      return effects().reply(done());
    }
    logger.info("Creating product '{}'", product.productId());
    return effects()
        .persist(new ProductCreated(
            product.productId(),
            product.productName(),
            product.category(),
            product.description(),
            product.eligibility()))
        .thenReply(__ -> done());
  }

  public Effect<Done> update(Product product) {
    if (!isActive()) {
      return effects().error("Product '" + commandContext().entityId() + "' does not exist");
    }
    logger.info("Updating product '{}'", commandContext().entityId());
    return effects()
        .persist(new ProductUpdated(
            product.productName(),
            product.category(),
            product.description(),
            product.eligibility()))
        .thenReply(__ -> done());
  }

  public Effect<Done> delete() {
    if (!isActive()) {
      return effects().error("Product '" + commandContext().entityId() + "' does not exist");
    }
    logger.info("Deleting product '{}'", commandContext().entityId());
    return effects()
        .persist(new ProductDeleted())
        .thenReply(__ -> done());
  }

  @Override
  public Product applyEvent(ProductEvent event) {
    return switch (event) {
      case ProductCreated created -> new Product(
          created.productId(),
          created.productName(),
          created.category(),
          created.description(),
          created.eligibility()
      );
      case ProductUpdated updated -> new Product(
          currentState().productId(),
          updated.productName(),
          updated.category(),
          updated.description(),
          updated.eligibility()
      );
      case ProductDeleted deleted -> new Product(
          currentState().productId(),
          currentState().productName(),
          currentState().category(),
          currentState().description(),
          currentState().eligibility(),
          true
      );
    };
  }
}
