package com.microapp.product.application;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.Done;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import com.microapp.product.domain.Product;
import com.microapp.product.domain.ProductEvent;
import com.microapp.product.domain.ProductEvent.ProductCreated;
import com.microapp.product.domain.ProductEvent.ProductUpdated;
import com.microapp.product.domain.ProductEvent.ProductDeleted;
import org.junit.jupiter.api.Test;

public class ProductEntityTest {

  private final Product sampleProduct = new Product(
      "travel_rewards_card",
      "Travel Rewards Platinum Card",
      "Credit Card",
      "Earn 3x points on travel purchases",
      "Customers with high travel spending"
  );

  @Test
  public void shouldCreateProduct() {
    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
        EventSourcedTestKit.of(ProductEntity::new);

    EventSourcedResult<Done> result = testKit
        .method(ProductEntity::create)
        .invoke(sampleProduct);

    assertEquals(done(), result.getReply());

    ProductCreated created = result.getNextEventOfType(ProductCreated.class);
    assertEquals("travel_rewards_card", created.productId());
    assertEquals("Travel Rewards Platinum Card", created.productName());
    assertEquals("Credit Card", created.category());

    Product state = testKit.getState();
    assertNotNull(state);
    assertFalse(state.deleted());
    assertEquals("travel_rewards_card", state.productId());
    assertEquals("Travel Rewards Platinum Card", state.productName());
    assertEquals("Credit Card", state.category());
    assertEquals("Earn 3x points on travel purchases", state.description());
    assertEquals("Customers with high travel spending", state.eligibility());
  }

  @Test
  public void shouldBeIdempotentOnDuplicateCreate() {
    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
        EventSourcedTestKit.of(ProductEntity::new);

    testKit.method(ProductEntity::create).invoke(sampleProduct);

    EventSourcedResult<Done> result = testKit
        .method(ProductEntity::create)
        .invoke(sampleProduct);

    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().isEmpty());
  }

  @Test
  public void shouldGetProduct() {
    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
        EventSourcedTestKit.of(ProductEntity::new);

    testKit.method(ProductEntity::create).invoke(sampleProduct);

    EventSourcedResult<Product> result = testKit
        .method(ProductEntity::getProduct)
        .invoke();

    Product product = result.getReply();
    assertEquals("travel_rewards_card", product.productId());
    assertEquals("Travel Rewards Platinum Card", product.productName());
  }

  @Test
  public void shouldFailGetOnNonExistentProduct() {
    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
        EventSourcedTestKit.of(ProductEntity::new);

    EventSourcedResult<Product> result = testKit
        .method(ProductEntity::getProduct)
        .invoke();

    assertTrue(result.isError());
  }

  @Test
  public void shouldUpdateProduct() {
    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
        EventSourcedTestKit.of(ProductEntity::new);

    testKit.method(ProductEntity::create).invoke(sampleProduct);

    Product updated = new Product(
        "travel_rewards_card",
        "Travel Rewards Gold Card",
        "Credit Card",
        "Earn 2x points on travel purchases",
        "All customers"
    );

    EventSourcedResult<Done> result = testKit
        .method(ProductEntity::update)
        .invoke(updated);

    assertEquals(done(), result.getReply());

    ProductUpdated event = result.getNextEventOfType(ProductUpdated.class);
    assertEquals("Travel Rewards Gold Card", event.productName());
    assertEquals("Earn 2x points on travel purchases", event.description());
    assertEquals("All customers", event.eligibility());

    Product state = testKit.getState();
    assertEquals("travel_rewards_card", state.productId());
    assertEquals("Travel Rewards Gold Card", state.productName());
    assertEquals("Earn 2x points on travel purchases", state.description());
    assertEquals("All customers", state.eligibility());
  }

  @Test
  public void shouldFailUpdateOnNonExistentProduct() {
    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
        EventSourcedTestKit.of(ProductEntity::new);

    EventSourcedResult<Done> result = testKit
        .method(ProductEntity::update)
        .invoke(sampleProduct);

    assertTrue(result.isError());
  }

  @Test
  public void shouldDeleteProduct() {
    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
        EventSourcedTestKit.of(ProductEntity::new);

    testKit.method(ProductEntity::create).invoke(sampleProduct);

    EventSourcedResult<Done> result = testKit
        .method(ProductEntity::delete)
        .invoke();

    assertEquals(done(), result.getReply());
    result.getNextEventOfType(ProductDeleted.class);
    assertTrue(testKit.getState().deleted());
  }

  @Test
  public void shouldFailDeleteOnNonExistentProduct() {
    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
        EventSourcedTestKit.of(ProductEntity::new);

    EventSourcedResult<Done> result = testKit
        .method(ProductEntity::delete)
        .invoke();

    assertTrue(result.isError());
  }

  @Test
  public void shouldFailGetAfterDelete() {
    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
        EventSourcedTestKit.of(ProductEntity::new);

    testKit.method(ProductEntity::create).invoke(sampleProduct);
    testKit.method(ProductEntity::delete).invoke();

    EventSourcedResult<Product> result = testKit
        .method(ProductEntity::getProduct)
        .invoke();

    assertTrue(result.isError());
  }

  @Test
  public void shouldAllowRecreateAfterDelete() {
    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
        EventSourcedTestKit.of(ProductEntity::new);

    testKit.method(ProductEntity::create).invoke(sampleProduct);
    testKit.method(ProductEntity::delete).invoke();

    Product newProduct = new Product(
        "travel_rewards_card",
        "Travel Rewards V2",
        "Premium Credit Card",
        "Earn 5x points on travel",
        "Premium customers"
    );

    EventSourcedResult<Done> result = testKit
        .method(ProductEntity::create)
        .invoke(newProduct);

    assertEquals(done(), result.getReply());
    result.getNextEventOfType(ProductCreated.class);

    Product state = testKit.getState();
    assertEquals("Travel Rewards V2", state.productName());
    assertEquals("Premium Credit Card", state.category());
  }

  @Test
  public void shouldPreserveProductIdOnUpdate() {
    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
        EventSourcedTestKit.of(ProductEntity::new);

    testKit.method(ProductEntity::create).invoke(sampleProduct);

    Product updated = new Product(
        "different_id",
        "New Name",
        "New Category",
        "New Description",
        "New Eligibility"
    );

    testKit.method(ProductEntity::update).invoke(updated);

    Product state = testKit.getState();
    assertEquals("travel_rewards_card", state.productId());
    assertEquals("New Name", state.productName());
  }
}
