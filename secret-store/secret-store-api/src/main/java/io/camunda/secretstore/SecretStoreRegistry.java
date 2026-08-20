/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * The store ID a {@code camunda.secrets.<name>} reference addresses. The syntax carries no store
   * dimension, so it names the default store; once several stores are supported, {@code
   * camunda.secrets.X} keeps meaning {@code camunda.secrets.default.X}.
   */
  public static final String DEFAULT_STORE_ID = "default";

  private final Map<String, LocallyCachedSecretStore> stores;

  public SecretStoreRegistry(final Map<String, SecretStore> stores) {
    this(stores, Map.of());
  }

  /**
   * Creates a registry whose stores cache in the given caches instead of the default {@link
   * CaffeineSecretCache}. This is the seam for a test double, or a per-store cache the caller wants
   * instead of the default. A store the caches do not cover still gets the default cache, and a
   * store that caches natively is left as it is, so every store handed out holds what it resolved
   * whichever constructor is used.
   *
   * <p>Uses the system clock for any store the caches do not cover — see the three-argument
   * constructor to drive the default cache's expiry from another time source (production wiring
   * passes the actor clock, so {@code /actuator/clock} time travel reaches it).
   *
   * <p>A cache given here is used as it is and is not instrumented: whether it reports on {@link
   * SecretCacheMetricsDoc}'s meters is the caller's business, and an arbitrary {@link SecretCache}
   * exposes nothing to measure. See the {@link SecretCacheFactory} constructor for the seam a
   * caller that does instrument its caches has to use.
   *
   * @throws IllegalArgumentException if a cache is given for a store ID nothing is configured for,
   *     or if two store IDs are given the same cache instance — the cache is keyed by the bare
   *     secret name, so a shared instance would let one store's value answer for another store's
   *     secret of the same name
   */
  public SecretStoreRegistry(
      final Map<String, SecretStore> stores, final Map<String, SecretCache> caches) {
    this(stores, caches, InstantSource.system());
  }

  /**
   * Creates a registry as the {@code Map}-and-{@code Map} constructor does, but drives the default
   * cache's expiry from {@code timeSource} instead of the system clock — the choice of default
   * cache is already the registry's job, and that default is now time-driven.
   *
   * <p>The default caches publish nothing on {@link SecretCacheMetricsDoc}'s meters: see the {@link
   * SecretCacheFactory} constructor for the seam that builds instrumented ones.
   */
  public SecretStoreRegistry(
      final Map<String, SecretStore> stores,
      final Map<String, SecretCache> caches,
      final InstantSource timeSource) {
    rejectUnusableCaches(stores, caches);
    this.stores =
        locallyCachedStores(
            stores,
            storeId -> {
              final var configured = caches.get(storeId);
              return configured != null
                  ? configured
                  : CaffeineSecretCache.createDefault(timeSource);
            });
  }

  /**
   * Creates a registry whose stores cache in whatever {@code cacheFactory} builds for them. This is
   * the seam for a caller that instruments the caches it builds: the factory is called only for a
   * store this registry actually wraps, so a store that caches natively — for which no cache is
   * built at all — shows up on none of {@link SecretCacheMetricsDoc}'s meters, as they document.
   *
   * <p>Both rules the {@code Map}-based constructors validate hold by construction here: the
   * factory is called once per configured store, so it can neither be given a cache for a store
   * nothing is configured for nor share one instance between two store IDs — as long as it returns
   * a fresh cache per ID, which is its documented contract.
   */
  public SecretStoreRegistry(
      final Map<String, SecretStore> stores, final SecretCacheFactory cacheFactory) {
    this.stores = locallyCachedStores(stores, cacheFactory);
  }

  /**
   * Returns all configured stores as locally cached stores, keyed by store ID.
   *
   * <p>A lookup in this map is exact, so a store ID that names no configured store addresses none.
   * A {@code camunda.secrets.<name>} reference addresses {@link #DEFAULT_STORE_ID}.
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
      final Map<String, SecretStore> stores, final SecretCacheFactory cacheFactory) {
    final Map<String, LocallyCachedSecretStore> locallyCached = new LinkedHashMap<>();
    stores.forEach(
        (storeId, store) ->
            locallyCached.put(storeId, locallyCached(storeId, store, cacheFactory)));
    return Collections.unmodifiableMap(locallyCached);
  }

  /**
   * Returns the store itself when it caches natively — wrapping it would put a second cache in
   * front of its own — and otherwise the store behind the cache the factory builds for it.
   *
   * <p>The factory is not called at all for a natively caching store, which is what keeps such a
   * store off {@link SecretCacheMetricsDoc}'s meters however the caller instruments the caches it
   * builds: there is no cache here to measure, and its own is the SDK's business.
   */
  private static LocallyCachedSecretStore locallyCached(
      final String storeId, final SecretStore store, final SecretCacheFactory cacheFactory) {
    if (store instanceof final LocallyCachedSecretStore locallyCached) {
      return locallyCached;
    }
    return new CachingSecretStore(store, cacheFactory.create(storeId));
  }
}
