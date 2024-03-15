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

/** Contains mapping between products and their JWT credentials */
public class JwtConfig {

  private final Map<Product, JwtCredential> map;

  public JwtConfig() {
    map = new HashMap<>();
  }

  public void addProduct(final Product product, final JwtCredential jwtCredential) {
    map.put(product, jwtCredential);
  }

  public JwtCredential getProduct(final Product product) {
    return map.get(product);
  }

  public Map<Product, JwtCredential> getMap() {
    return map;
  }
}
