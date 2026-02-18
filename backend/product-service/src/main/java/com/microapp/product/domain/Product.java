package com.microapp.product.domain;

public record Product(
    String productId,
    String productName,
    String category,
    String description,
    String eligibility,
    boolean deleted
) {

  public Product(String productId, String productName, String category,
                 String description, String eligibility) {
    this(productId, productName, category, description, eligibility, false);
  }

  public Product withName(String newName) {
    return new Product(productId, newName, category, description, eligibility);
  }

  public Product withCategory(String newCategory) {
    return new Product(productId, productName, newCategory, description, eligibility);
  }

  public Product withDescription(String newDescription) {
    return new Product(productId, productName, category, newDescription, eligibility);
  }

  public Product withEligibility(String newEligibility) {
    return new Product(productId, productName, category, description, newEligibility);
  }
}
