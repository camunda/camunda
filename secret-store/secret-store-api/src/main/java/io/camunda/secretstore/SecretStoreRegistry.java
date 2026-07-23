/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

/**
 * Provides the configured {@link SecretStore}s for the current physical tenant, keyed by store ID.
 */
@NullMarked
public final class SecretStoreRegistry {

  private final Map<String, SecretStore> stores;
  private final Map<String, SecretCache> caches;

  public SecretStoreRegistry(final Map<String, SecretStore> stores) {
    this(
        stores,
        stores.keySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(name -> name, name -> new InMemorySecretCache())));
  }

  /**
   * Creates a registry with the given caches instead of one in-memory cache per store. Primarily
   * for tests that need custom cache behavior.
   */
  public SecretStoreRegistry(
      final Map<String, SecretStore> stores, final Map<String, SecretCache> caches) {
    this.stores = stores;
    this.caches = caches;
  }

  /** Returns all configured secret stores, keyed by store ID. */
  public Map<String, SecretStore> getStores() {
    return stores;
  }

  /** Returns one cache per configured store, keyed by store ID. */
  public Map<String, SecretCache> getCaches() {
    return caches;
  }
}
