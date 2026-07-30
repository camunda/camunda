/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Provides the configured {@link SecretStore}s for the current physical tenant, keyed by store ID,
 * along with the {@link SecretCache} in front of each of them.
 *
 * <p>Every configured store has a cache: a caller may look one up by store ID without handling a
 * missing entry.
 */
@NullMarked
public final class SecretStoreRegistry {

  private final Map<String, SecretStore> stores;
  private final Map<String, SecretCache> caches;

  public SecretStoreRegistry(final Map<String, SecretStore> stores) {
    this(stores, Map.of());
  }

  /**
   * Creates a registry with the given caches instead of the default in-memory one. A store the
   * caches do not cover still gets an in-memory cache, so the one-cache-per-store invariant holds
   * whichever constructor is used. Primarily for tests that need custom cache behavior.
   */
  public SecretStoreRegistry(
      final Map<String, SecretStore> stores, final Map<String, SecretCache> caches) {
    this.stores = stores;
    this.caches = withACachePerStore(stores, caches);
  }

  /** Returns all configured secret stores, keyed by store ID. */
  public Map<String, SecretStore> getStores() {
    return stores;
  }

  /** Returns one cache per configured store, keyed by store ID. */
  public Map<String, SecretCache> getCaches() {
    return caches;
  }

  private static Map<String, SecretCache> withACachePerStore(
      final Map<String, SecretStore> stores, final Map<String, SecretCache> caches) {
    final Map<String, SecretCache> perStore = new LinkedHashMap<>(caches);
    stores
        .keySet()
        .forEach(
            storeId -> perStore.computeIfAbsent(storeId, ignored -> new InMemorySecretCache()));
    return Collections.unmodifiableMap(perStore);
  }
}
