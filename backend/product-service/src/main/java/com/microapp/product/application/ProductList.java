package com.microapp.product.application;

import java.util.Collection;

public record ProductList(Collection<ProductSummary> products) {}
