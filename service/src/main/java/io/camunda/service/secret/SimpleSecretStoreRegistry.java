/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.secret;

/**
 * Holds the secret stores known to the gateway and exposes the default one used for resolution.
 *
 * <p>PoC scope: a single default store. Future iterations may support multiple stores with
 * per-reference routing (e.g. {@code camunda.secrets.<storeId>.<name>}).
 */
public final class SimpleSecretStoreRegistry {

  private final SecretStore defaultStore;

  public SimpleSecretStoreRegistry(final SecretStore defaultStore) {
    this.defaultStore = defaultStore;
  }

  public SecretStore getDefaultStore() {
    return defaultStore;
  }
}
