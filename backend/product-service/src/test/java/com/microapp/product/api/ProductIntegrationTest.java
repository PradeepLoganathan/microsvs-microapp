package com.microapp.product.api;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import com.microapp.product.application.ProductEntity;
import com.microapp.product.domain.Product;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ProductIntegrationTest extends TestKitSupport {

  private Product sampleProduct(String id) {
    return new Product(id, "Test Product", "Test Category",
        "Test description", "All customers");
  }

  private void createViaEntity(String id, Product product) {
    var result = componentClient
        .forEventSourcedEntity(id)
        .method(ProductEntity::create)
        .invoke(product);
    assertEquals(done(), result);
  }

  private Product getViaEntity(String id) {
    return componentClient
        .forEventSourcedEntity(id)
        .method(ProductEntity::getProduct)
        .invoke();
  }

  @Test
  public void shouldCreateProductViaHttp() {
    String id = UUID.randomUUID().toString();
    Product product = sampleProduct(id);

    var response = httpClient
        .POST("/products/" + id)
        .withRequestBody(product)
        .invoke();

    assertEquals(StatusCodes.CREATED, response.status());
    assertEquals("Test Product", getViaEntity(id).productName());
  }

  @Test
  public void shouldGetProductViaHttp() {
    String id = UUID.randomUUID().toString();
    createViaEntity(id, sampleProduct(id));

    var response = httpClient
        .GET("/products/" + id)
        .responseBodyAs(Product.class)
        .invoke();

    assertEquals(StatusCodes.OK, response.status());
    assertEquals("Test Product", response.body().productName());
    assertEquals("Test Category", response.body().category());
    assertEquals(id, response.body().productId());
  }

  @Test
  public void shouldGetNonExistentProductReturn500() {
    String id = UUID.randomUUID().toString();

    assertThrows(Exception.class, () ->
        httpClient
            .GET("/products/" + id)
            .responseBodyAs(Product.class)
            .invoke()
    );
  }

  @Test
  public void shouldUpdateProductViaHttp() {
    String id = UUID.randomUUID().toString();
    createViaEntity(id, sampleProduct(id));

    Product updated = new Product(id, "Updated Product", "Updated Category",
        "Updated description", "Premium customers");

    var response = httpClient
        .PUT("/products/" + id)
        .withRequestBody(updated)
        .invoke();

    assertEquals(StatusCodes.OK, response.status());

    Product result = getViaEntity(id);
    assertEquals("Updated Product", result.productName());
    assertEquals("Updated Category", result.category());
    assertEquals("Updated description", result.description());
    assertEquals("Premium customers", result.eligibility());
  }

  @Test
  public void shouldDeleteProductViaHttp() {
    String id = UUID.randomUUID().toString();
    createViaEntity(id, sampleProduct(id));

    var response = httpClient
        .DELETE("/products/" + id)
        .invoke();

    assertEquals(StatusCodes.OK, response.status());

    assertThrows(Exception.class, () -> getViaEntity(id));
  }

  @Test
  public void shouldSeedProducts() {
    var response = httpClient
        .POST("/products/seed")
        .invoke();

    assertEquals(StatusCodes.OK, response.status());

    Product travel = getViaEntity("travel_rewards_card");
    assertEquals("Travel Rewards Platinum Card", travel.productName());
    assertEquals("Credit Card", travel.category());

    Product health = getViaEntity("health_insurance_basic");
    assertEquals("HealthGuard Basic Plan", health.productName());
    assertEquals("Insurance", health.category());

    Product cashback = getViaEntity("cashback_everyday_card");
    assertEquals("Cashback Everyday Card", cashback.productName());

    Product billPay = getViaEntity("bill_pay_assist");
    assertEquals("SmartBill Pay Assistant", billPay.productName());
    assertEquals("Banking Service", billPay.category());
  }

  @Test
  public void shouldGetAllProductsAfterSeed() {
    httpClient.POST("/products/seed").invoke();

    var response = httpClient
        .GET("/products/")
        .responseBodyAs(List.class)
        .invoke();

    assertEquals(StatusCodes.OK, response.status());
    assertEquals(4, response.body().size());
  }

  @Test
  public void shouldUpdatePreserveProductId() {
    String id = UUID.randomUUID().toString();
    createViaEntity(id, sampleProduct(id));

    Product updated = new Product("different_id", "New Name", "New Category",
        "New desc", "New eligibility");

    httpClient
        .PUT("/products/" + id)
        .withRequestBody(updated)
        .invoke();

    Product result = getViaEntity(id);
    assertEquals(id, result.productId());
    assertEquals("New Name", result.productName());
  }

  @Test
  public void shouldSeedBeIdempotent() {
    httpClient.POST("/products/seed").invoke();
    httpClient.POST("/products/seed").invoke();

    Product travel = getViaEntity("travel_rewards_card");
    assertEquals("Travel Rewards Platinum Card", travel.productName());
  }
}
