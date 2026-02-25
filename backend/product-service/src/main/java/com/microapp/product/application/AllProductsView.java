package com.microapp.product.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.microapp.product.domain.ProductEvent;
import com.microapp.product.domain.ProductEvent.ProductCreated;
import com.microapp.product.domain.ProductEvent.ProductUpdated;
import com.microapp.product.domain.ProductEvent.ProductDeleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "all-products")
public class AllProductsView extends View {

  private static final Logger logger = LoggerFactory.getLogger(AllProductsView.class);

  @Consume.FromEventSourcedEntity(ProductEntity.class)
  public static class ProductTableUpdater extends TableUpdater<ProductSummary> {

    public Effect<ProductSummary> onEvent(ProductEvent event) {
      return switch (event) {
        case ProductCreated created -> {
          logger.info("View indexing product '{}'", created.productId());
          yield effects().updateRow(new ProductSummary(
              created.productId(),
              created.productName(),
              created.category(),
              created.description(),
              created.eligibility()
          ));
        }
        case ProductUpdated updated -> {
          var current = rowState();
          logger.info("View updating product '{}'", current.productId());
          yield effects().updateRow(new ProductSummary(
              current.productId(),
              updated.productName(),
              updated.category(),
              updated.description(),
              updated.eligibility()
          ));
        }
        case ProductDeleted deleted -> {
          logger.info("View removing product '{}'", rowState().productId());
          yield effects().deleteRow();
        }
      };
    }
  }

  @Query("SELECT * AS products FROM all_products")
  public QueryEffect<ProductList> getAllProducts() {
    return queryResult();
  }
}
