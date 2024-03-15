/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.auth;

import java.util.HashMap;
import java.util.Map;

/** Contains mapping between products and their Simple credentials */
public class SimpleConfig {

  private final Map<Product, SimpleCredential> map;

  public SimpleConfig() {
    map = new HashMap<>();
  }

  public void addProduct(final Product product, final SimpleCredential simpleCredential) {
    map.put(product, simpleCredential);
  }

  public Map<Product, SimpleCredential> getMap() {
    return map;
  }

  public SimpleCredential getProduct(final Product product) {
    return map.get(product);
  }
}
