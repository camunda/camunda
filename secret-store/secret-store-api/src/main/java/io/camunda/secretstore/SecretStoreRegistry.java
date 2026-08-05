/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
   * cache, so every store handed out holds what it resolved whichever constructor is used.
   *
   * <p>A cache given for a store that caches natively is ignored rather than put in front of it:
   * the store's own cache stands, so this seam reaches only the stores this registry wraps.
   *
   * @throws IllegalArgumentException if a cache is given for a store ID nothing is configured for,
   *     or if two store IDs are given the same cache instance — the cache is keyed by the bare
   *     secret name, so a shared instance would let one store's value answer for another store's
   *     secret of the same name
   */
  public SecretStoreRegistry(
      final Map<String, SecretStore> stores, final Map<String, SecretCache> caches) {
    rejectUnusableCaches(stores, caches);
    this.stores = locallyCachedStores(stores, caches);
  }

  /**
   * Returns all configured secret stores, keyed by store ID.
   *
   * <p>A lookup in this map is exact: an empty store ID addresses no store, even when a single
   * store is configured. Reading an empty store ID as the sole configured store is a rule of the
   * {@code camunda.secrets.<name>} reference syntax and belongs to the caller that knows it.
   */
  public Map<String, LocallyCachedSecretStore> getStores() {
    return stores;
  }

  /**
   * Rejects a set of caches that cannot be used as given: one for a store that is not configured,
   * or one instance shared by two store IDs. Both are configuration mistakes rather than states to
   * recover from, and a shared instance would silently break the collision-freedom this class
   * documents.
   */
  private static void rejectUnusableCaches(
      final Map<String, SecretStore> stores, final Map<String, SecretCache> caches) {
    final var unconfigured = new ArrayList<>(caches.keySet());
    unconfigured.removeAll(stores.keySet());
    if (!unconfigured.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected a secret cache only for a configured secret store, but got one for "
              + unconfigured
              + ", which nothing is configured for; configured stores are "
              + stores.keySet());
    }

    final Map<SecretCache, List<String>> storeIdsByCache = new IdentityHashMap<>();
    caches.forEach(
        (storeId, cache) ->
            storeIdsByCache.computeIfAbsent(cache, c -> new ArrayList<>()).add(storeId));
    final var shared =
        storeIdsByCache.values().stream().filter(storeIds -> storeIds.size() > 1).toList();
    if (!shared.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected each secret store to have its own secret cache, but the stores "
              + shared
              + " share one; a cache is keyed by the bare secret name, so a shared instance would "
              + "let one store's value answer for another store's secret of the same name");
    }
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
    final var cache = Optional.ofNullable(caches.get(storeId)).orElseGet(InMemorySecretCache::new);
    return new CachingSecretStore(store, cache);
  }
}
