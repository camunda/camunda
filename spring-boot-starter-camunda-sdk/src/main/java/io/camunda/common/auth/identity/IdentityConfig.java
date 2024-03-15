/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.auth.identity;

import io.camunda.common.auth.Product;
import java.util.HashMap;
import java.util.Map;

/** Contains mapping between products and their Identity and IdentityConfiguration */
public class IdentityConfig {

  private final Map<Product, IdentityContainer> map;

  public IdentityConfig() {
    map = new HashMap<>();
  }

  public void addProduct(final Product product, final IdentityContainer identityContainer) {
    map.put(product, identityContainer);
  }

  public IdentityContainer get(final Product product) {
    return map.get(product);
  }
}
