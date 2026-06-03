package com.microapp.product.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import com.microapp.product.application.AllProductsView;
import com.microapp.product.application.ProductEntity;
import com.microapp.product.application.ProductSummary;
import com.microapp.product.domain.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/products")
public class ProductEndpoint extends AbstractHttpEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(ProductEndpoint.class);

  private final ComponentClient componentClient;

  public ProductEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/{productId}")
  public HttpResponse create(String productId, Product product) {
    logger.info("Creating product: {}", productId);
    componentClient
        .forEventSourcedEntity(productId)
        .method(ProductEntity::create)
        .invoke(product);
    return HttpResponses.created();
  }

  @Get("/{productId}")
  public Product getProduct(String productId) {
    return componentClient
        .forEventSourcedEntity(productId)
        .method(ProductEntity::getProduct)
        .invoke();
  }

  @Put("/{productId}")
  public HttpResponse update(String productId, Product product) {
    logger.info("Updating product: {}", productId);
    componentClient
        .forEventSourcedEntity(productId)
        .method(ProductEntity::update)
        .invoke(product);
    return HttpResponses.ok();
  }

  @Delete("/{productId}")
  public HttpResponse delete(String productId) {
    logger.info("Deleting product: {}", productId);
    componentClient
        .forEventSourcedEntity(productId)
        .method(ProductEntity::delete)
        .invoke();
    return HttpResponses.ok();
  }

  @Get("/")
  public Collection<ProductSummary> getAllProducts() {
    return componentClient
        .forView()
        .method(AllProductsView::getAllProducts)
        .invoke()
        .products();
  }

  @Post("/seed")
  public HttpResponse seed() {
    logger.info("Seeding product catalog...");

    createProduct("travel_rewards_card", "Travel Rewards Platinum Card", "Credit Card",
        "Earn 3x points on travel purchases, no foreign transaction fees, complimentary lounge access",
        "Customers with high travel spending");

    createProduct("health_insurance_basic", "HealthGuard Basic Plan", "Insurance",
        "Comprehensive coverage for medical, dental, and vision with low copays and preventive care",
        "Customers with regular health-related spending");

    createProduct("cashback_everyday_card", "Cashback Everyday Card", "Credit Card",
        "2% cashback on groceries and utilities, 1% on everything else, no annual fee",
        "Customers with high grocery and utility spending");

    createProduct("bill_pay_assist", "SmartBill Pay Assistant", "Banking Service",
        "Automated bill payments with smart scheduling, payment reminders, and late fee protection",
        "Customers with recurring utility and subscription payments");

    createProduct("home_financing_i", "MBSB Home Financing-i", "Home Financing",
        "Shariah-compliant home financing up to 90% margin, profit rate from 3.8% p.a., "
            + "tenure up to 35 years, with fast pre-approval",
        "Customers with steady income and a healthy monthly surplus or recurring rent payments");

    logger.info("Product catalog seeded successfully");
    return HttpResponses.ok();
  }

  private void createProduct(String id, String name, String category,
                             String description, String eligibility) {
    componentClient
        .forEventSourcedEntity(id)
        .method(ProductEntity::create)
        .invoke(new Product(id, name, category, description, eligibility));
  }
}
