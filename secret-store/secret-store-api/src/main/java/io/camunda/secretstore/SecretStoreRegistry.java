/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
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
   * <p>A cache given here is used as it is and is not instrumented: only the default cache this
   * registry builds itself reports on {@link SecretCacheMetricsDoc}'s meters, since an arbitrary
   * {@link SecretCache} exposes nothing to measure.
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
   * Creates a registry as the two-argument constructor does, but drives the default cache's expiry
   * from {@code timeSource} instead of the system clock — the choice of default cache is already
   * the registry's job, and that default is now time-driven.
   *
   * <p>The default caches publish nothing: see the four-argument constructor to have them report on
   * a registry.
   */
  public SecretStoreRegistry(
      final Map<String, SecretStore> stores,
      final Map<String, SecretCache> caches,
      final InstantSource timeSource) {
    this(stores, caches, timeSource, new CompositeMeterRegistry());
  }

  /**
   * Creates a registry as the three-argument constructor does, and has each default cache publish
   * what it does on {@code meterRegistry}, tagged with the store ID it was built for. Choosing the
   * default cache is the registry's job, so registering its meters is too — no caller can reach a
   * cache to instrument it afterwards.
   *
   * <p>A store ID is unique only within one physical tenant, and this registry knows nothing about
   * tenants. A caller that builds one registry per tenant must therefore pass a registry that
   * already carries the tenant as a tag, otherwise two tenants using the same store ID register the
   * same series twice: Micrometer hands back the meter that already exists, and the two caches
   * silently share it.
   */
  public SecretStoreRegistry(
      final Map<String, SecretStore> stores,
      final Map<String, SecretCache> caches,
      final InstantSource timeSource,
      final MeterRegistry meterRegistry) {
    rejectUnusableCaches(stores, caches);
    this.stores = locallyCachedStores(stores, caches, timeSource, meterRegistry);
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
      final Map<String, SecretStore> stores,
      final Map<String, SecretCache> caches,
      final InstantSource timeSource,
      final MeterRegistry meterRegistry) {
    final Map<String, LocallyCachedSecretStore> locallyCached = new LinkedHashMap<>();
    stores.forEach(
        (storeId, store) ->
            locallyCached.put(
                storeId, locallyCached(storeId, store, caches, timeSource, meterRegistry)));
    return Collections.unmodifiableMap(locallyCached);
  }

  /**
   * Returns the store itself when it caches natively — wrapping it would put a second cache in
   * front of its own — and otherwise the store behind the cache configured for it, or a fresh
   * time-and-size-bounded one when the configured caches do not cover it.
   *
   * <p>Only that fresh cache is instrumented, which is also why a natively caching store shows up
   * on none of {@link SecretCacheMetricsDoc}'s meters: there is no cache here to measure, and its
   * own is the SDK's business.
   */
  private static LocallyCachedSecretStore locallyCached(
      final String storeId,
      final SecretStore store,
      final Map<String, SecretCache> caches,
      final InstantSource timeSource,
      final MeterRegistry meterRegistry) {
    if (store instanceof final LocallyCachedSecretStore locallyCached) {
      return locallyCached;
    }
    final var configured = caches.get(storeId);
    return new CachingSecretStore(
        store,
        configured != null
            ? configured
            : CaffeineSecretCache.createDefault(timeSource, meterRegistry, storeId));
  }
}
