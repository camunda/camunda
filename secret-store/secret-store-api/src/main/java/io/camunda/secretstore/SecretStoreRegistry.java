/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Provides the configured {@link SecretStore}s for the current physical tenant, keyed by store ID.
 *
 * <p>A store is the only thing handed out, and always as a {@link LocallyCachedSecretStore}: it
 * holds what it resolves itself, so a caller resolves secrets through {@link SecretStore#resolve}
 * alone and cannot read past the cache by accident. A store that does not cache natively is wrapped
 * in a {@link CachingSecretStore} to make it one. Each store holds its own values, so entries of
 * different stores never collide.
 */
@NullMarked
public final class SecretStoreRegistry {

  private final Map<String, LocallyCachedSecretStore> stores;

  public SecretStoreRegistry(final Map<String, SecretStore> stores) {
    this(stores, Map.of());
  }

  /**
   * Creates a registry whose stores cache in the given caches instead of the default in-memory one.
   * This is the seam for a cache implementation other than {@link InMemorySecretCache} — a TTL and
   * eviction variant, or a test double. A store the caches do not cover still gets an in-memory
   * cache, and a store that caches natively is left as it is, so every store handed out holds what
   * it resolved whichever constructor is used.
   */
  public SecretStoreRegistry(
      final Map<String, SecretStore> stores, final Map<String, SecretCache> caches) {
    this.stores = locallyCachedStores(stores, caches);
  }

  /**
   * Returns all configured secret stores, keyed by store ID.
   *
   * <p>A lookup in this map is exact: an empty store ID addresses no store, even when a single
   * store is configured. Only {@link #findStoreForReference} applies the sole-store rule below.
   */
  public Map<String, LocallyCachedSecretStore> getStores() {
    return stores;
  }

  /**
   * Returns the store the secret reference addresses, or empty when it addresses none.
   *
   * <p>The {@code camunda.secrets.<name>} syntax carries no store dimension yet, so an empty store
   * ID addresses the sole configured store; with several stores an empty store ID is ambiguous and
   * addresses none.
   */
  public Optional<LocallyCachedSecretStore> findStoreForReference(final String storeId) {
    if (!storeId.isEmpty()) {
      return Optional.ofNullable(stores.get(storeId));
    }
    return stores.size() == 1 ? Optional.of(stores.values().iterator().next()) : Optional.empty();
  }

  private static Map<String, LocallyCachedSecretStore> locallyCachedStores(
      final Map<String, SecretStore> stores, final Map<String, SecretCache> caches) {
    final Map<String, LocallyCachedSecretStore> locallyCached = new LinkedHashMap<>();
    stores.forEach(
        (storeId, store) -> locallyCached.put(storeId, locallyCached(storeId, store, caches)));
    return Collections.unmodifiableMap(locallyCached);
  }

  /**
   * Returns the store itself when it caches natively — wrapping it would put a second cache in
   * front of its own — and otherwise the store behind the cache configured for it, or a fresh
   * in-memory one when the configured caches do not cover it.
   */
  private static LocallyCachedSecretStore locallyCached(
      final String storeId, final SecretStore store, final Map<String, SecretCache> caches) {
    if (store instanceof final LocallyCachedSecretStore locallyCached) {
      return locallyCached;
    }
    final var cache = requireNonNull(caches.getOrDefault(storeId, new InMemorySecretCache()));
    return new CachingSecretStore(store, cache);
  }
}
