/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecretStoreRegistryTest {

  private static final NoopSecretStore NOOP = new NoopSecretStore();

  @Test
  void shouldReturnEmptyStoresWhenNoneConfigured() {
    // given
    final var registry = new SecretStoreRegistry(Map.of());

    // when
    final var stores = registry.getStores();

    // then
    assertThat(stores).isEmpty();
  }

  @Test
  void shouldReturnConfiguredStore() {
    // given
    final var registry = new SecretStoreRegistry(Map.of("default", storeHolding("token", "value")));

    // when
    final var stores = registry.getStores();

    // then the configured store is what answers under its ID
    assertThat(stores).containsKey("default");
    assertThat(stores.get("default").resolve(Set.of("token")))
        .containsEntry("token", new Resolved("value"));
  }

  @Test
  void shouldReturnAllConfiguredStores() {
    // given
    final var registry = new SecretStoreRegistry(Map.of("store-a", NOOP, "store-b", NOOP));

    // when
    final var stores = registry.getStores();

    // then
    assertThat(stores).containsKeys("store-a", "store-b");
  }

  @Test
  void shouldNotAllowTheStoresToBeModified() {
    // given
    final var registry = new SecretStoreRegistry(Map.of("default", NOOP));

    // when / then the configured stores cannot be swapped out from the outside
    assertThatThrownBy(() -> registry.getStores().remove("default"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldUseProvidedCaches() {
    // given
    final var cache = new InMemorySecretCache();
    cache.put("token", "value");

    // when
    final var registry = new SecretStoreRegistry(Map.of("default", NOOP), Map.of("default", cache));

    // then the store caches in the given cache, so what it holds answers a local lookup
    assertThat(lookupLocal(registry, "default", "token")).contains("value");
  }

  @Test
  void shouldCacheAStoreTheProvidedCachesDoNotCover() {
    // given caches covering only one of the two configured stores
    final var cache = new InMemorySecretCache();
    cache.put("token", "covered-value");
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", NOOP, "store-b", storeHolding("token", "uncovered-value")),
            Map.of("store-a", cache));

    // when the uncovered store resolves a secret
    registry.getStores().get("store-b").resolve(Set.of("token"));

    // then it cached it too, so a caller never has to handle a store that cannot cache
    assertThat(lookupLocal(registry, "store-a", "token")).contains("covered-value");
    assertThat(lookupLocal(registry, "store-b", "token")).contains("uncovered-value");
  }

  @Test
  void shouldKeepTheCachedValuesOfTwoStoresApart() {
    // given two stores holding a different value under the same name
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", storeHolding("token", "a"), "store-b", storeHolding("token", "b")));

    // when only one of them resolves it
    registry.getStores().get("store-a").resolve(Set.of("token"));

    // then the other one holds nothing: what a store caches is its own
    assertThat(lookupLocal(registry, "store-a", "token")).contains("a");
    assertThat(lookupLocal(registry, "store-b", "token")).isEmpty();
  }

  @Test
  void shouldRejectACacheSharedByTwoStores() {
    // given one cache instance for two stores
    final var shared = new InMemorySecretCache();

    // when / then a cache is keyed by the bare secret name, so sharing one would let a store answer
    // with another store's value for the same name
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SecretStoreRegistry(
                    Map.of("store-a", NOOP, "store-b", NOOP),
                    Map.of("store-a", shared, "store-b", shared)))
        .withMessageContaining("store-a")
        .withMessageContaining("store-b");
  }

  @Test
  void shouldRejectACacheForAnUnconfiguredStore() {
    // given a cache for a store ID nothing is configured for
    final var cache = new InMemorySecretCache();

    // when / then the cache would never be used, which is a configuration mistake rather than a
    // state to recover from
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SecretStoreRegistry(Map.of("default", NOOP), Map.of("other", cache)))
        .withMessageContaining("other");
  }

  @Test
  void shouldHandOutAStoreThatCachesNativelyAsItIs() {
    // given a store that already holds what it resolves, as a cloud SDK with a client-side cache
    // does
    final var nativelyCaching = new NativelyCachingSecretStore();

    // when
    final var registry = new SecretStoreRegistry(Map.of("default", nativelyCaching));

    // then it is handed out unwrapped: wrapping it would put a second cache in front of its own
    assertThat(registry.getStores().get("default")).isSameAs(nativelyCaching);
  }

  @Test
  void shouldIgnoreACacheGivenForAStoreThatCachesNatively() {
    // given a store that caches natively and a cache configured for it
    final var nativelyCaching = new NativelyCachingSecretStore();
    final var cache = new InMemorySecretCache();
    cache.put("token", "value");

    // when
    final var registry =
        new SecretStoreRegistry(Map.of("default", nativelyCaching), Map.of("default", cache));

    // then the store's own cache stands and the given one is ignored, so this seam reaches only the
    // stores the registry wraps
    assertThat(registry.getStores().get("default")).isSameAs(nativelyCaching);
    assertThat(lookupLocal(registry, "default", "token")).isEmpty();
  }

  @Test
  void shouldExpireTheDefaultCacheOfAStoreAfterTheTtl() {
    // given a registry using the default cache, driven by an injected time source instead of the
    // system clock
    final var timeSource = new ControlledInstantSource(Instant.parse("2026-01-01T00:00:00Z"));
    final var registry =
        new SecretStoreRegistry(
            Map.of("default", storeHolding("token", "value")), Map.of(), timeSource);
    registry.getStores().get("default").resolve(Set.of("token"));
    assertThat(lookupLocal(registry, "default", "token")).contains("value");

    // when the default cache's TTL elapses
    timeSource.advance(CaffeineSecretCache.DEFAULT_TTL);

    // then the cached value is gone, proving the registry's construction site actually wires the
    // injected time source into the default cache
    assertThat(lookupLocal(registry, "default", "token")).isEmpty();
  }

  @Test
  void shouldKeepTheDefaultCachesOfTwoStoresApart() {
    // given two stores holding a different value under the same name, both on the default cache
    // (as opposed to shouldKeepTheCachedValuesOfTwoStoresApart, which covers the no-time-source
    // one-argument constructor)
    final var timeSource = new ControlledInstantSource(Instant.parse("2026-01-01T00:00:00Z"));
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", storeHolding("token", "a"), "store-b", storeHolding("token", "b")),
            Map.of(),
            timeSource);

    // when only one of them resolves it
    registry.getStores().get("store-a").resolve(Set.of("token"));

    // then the other one holds nothing: what a store caches is its own
    assertThat(lookupLocal(registry, "store-a", "token")).contains("a");
    assertThat(lookupLocal(registry, "store-b", "token")).isEmpty();
  }

  @Test
  void shouldResolveARotatedValueAfterTheTtlThroughTheRegistry() {
    // given a store whose value changes, resolved through the registry's default cache
    final var timeSource = new ControlledInstantSource(Instant.parse("2026-01-01T00:00:00Z"));
    final var store = new MutableSecretStore("token", "v1");
    final var registry = new SecretStoreRegistry(Map.of("default", store), Map.of(), timeSource);
    registry.getStores().get("default").resolve(Set.of("token"));

    // when the store's value changes before the TTL elapses
    store.holds("token", "v2");
    registry.getStores().get("default").resolve(Set.of("token"));

    // then the registry still answers with the cached value, and the store was not asked again
    assertThat(lookupLocal(registry, "default", "token")).contains("v1");
    assertThat(store.resolveCalls).containsExactly(Set.of("token"));

    // when the TTL elapses
    timeSource.advance(CaffeineSecretCache.DEFAULT_TTL);
    registry.getStores().get("default").resolve(Set.of("token"));

    // then the rotated value is picked up, with no restart required
    assertThat(lookupLocal(registry, "default", "token")).contains("v2");
    assertThat(store.resolveCalls).containsExactly(Set.of("token"), Set.of("token"));
  }

  @Test
  void shouldPublishWhatTheCacheOfEachStoreDoes() {
    // given two stores, each on a cache the factory built for its own store ID
    final var meterRegistry = new SimpleMeterRegistry();
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", storeHolding("token", "a"), "store-b", storeHolding("token", "b")),
            meteredCacheFactory(meterRegistry));

    // when only one of them resolves the name
    registry.getStores().get("store-a").resolve(Set.of("token"));
    registry.getStores().get("store-a").lookupLocal("token");

    // then each cache reports under its own store ID, so one store's numbers never answer for
    // another's — the registry is the only place that knows which cache belongs to which store
    assertThat(SecretCacheMeters.results(meterRegistry, "store-a", SecretCacheResult.MISS)).isOne();
    assertThat(SecretCacheMeters.results(meterRegistry, "store-a", SecretCacheResult.HIT)).isOne();
    assertThat(SecretCacheMeters.results(meterRegistry, "store-b", SecretCacheResult.MISS))
        .isZero();
  }

  @Test
  void shouldBuildNoCacheForAStoreThatCachesNatively() {
    // given a store that holds what it resolves itself
    final var meterRegistry = new SimpleMeterRegistry();
    final var storeIdsBuiltFor = new ArrayList<String>();
    final var registry =
        new SecretStoreRegistry(
            Map.of("default", new NativelyCachingSecretStore()),
            storeId -> {
              storeIdsBuiltFor.add(storeId);
              return meteredCacheFactory(meterRegistry).create(storeId);
            });

    // when it is resolved through
    registry.getStores().get("default").resolve(Set.of("token"));

    // then the factory was never called for it, so no cache meter exists either: wrapping such a
    // store would put a second cache in front of its own, and meters registered for a cache it
    // never resolves through would sit at zero forever
    assertThat(storeIdsBuiltFor).isEmpty();
    assertThat(SecretCacheMeters.cacheMeterNames(meterRegistry)).isEmpty();
  }

  @Test
  void shouldPublishNothingForACacheTheCallerSupplied() {
    // given a store whose cache the caller chose instead of an instrumented one
    final var meterRegistry = new SimpleMeterRegistry();
    final var registry =
        new SecretStoreRegistry(
            Map.of("default", storeHolding("token", "value")),
            Map.of("default", new InMemorySecretCache()),
            new ControlledInstantSource(Instant.parse("2026-01-01T00:00:00Z")));

    // when it is resolved through
    registry.getStores().get("default").resolve(Set.of("token"));

    // then nothing is published: an arbitrary SecretCache exposes nothing to measure, so this seam
    // stays a plain test double rather than half-instrumenting one
    assertThat(SecretCacheMeters.cacheMeterNames(meterRegistry)).isEmpty();
  }

  /** A factory building the instrumented cache the Spring wiring builds, as it builds it. */
  private static SecretCacheFactory meteredCacheFactory(final SimpleMeterRegistry meterRegistry) {
    return SecretCacheFactory.metered(
        CaffeineSecretCache.DEFAULT_MAX_SIZE,
        CaffeineSecretCache.DEFAULT_TTL,
        new ControlledInstantSource(Instant.parse("2026-01-01T00:00:00Z")),
        meterRegistry);
  }

  private static Optional<String> lookupLocal(
      final SecretStoreRegistry registry, final String storeId, final String name) {
    return registry.getStores().get(storeId).lookupLocal(name);
  }

  /** A store answering with the given value for the given name, and NOT_FOUND for anything else. */
  private static SecretStore storeHolding(final String name, final String value) {
    return new SecretStore() {
      @Override
      public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
        return resultsFor(names, Map.of(name, value));
      }

      @Override
      public List<String> list() {
        return List.of(name);
      }
    };
  }

  private static Map<String, SecretResolutionResult> resultsFor(
      final Set<String> names, final Map<String, String> values) {
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>();
    names.forEach(
        requested -> {
          final var value = values.get(requested);
          results.put(
              requested,
              value == null
                  ? new Failed(SecretErrorCode.NOT_FOUND, "no secret found: " + requested, null)
                  : new Resolved(value));
        });
    return results;
  }

  /** A store whose set of secrets can change, to tell a held value apart from the store's own. */
  private static final class MutableSecretStore implements SecretStore {

    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<Set<String>> resolveCalls = new ArrayList<>();

    private MutableSecretStore(final String name, final String value) {
      values.put(name, value);
    }

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      resolveCalls.add(Set.copyOf(names));
      return resultsFor(names, values);
    }

    @Override
    public List<String> list() {
      return List.copyOf(values.keySet());
    }

    void holds(final String name, final String value) {
      values.put(name, value);
    }
  }

  /**
   * A store that holds what it resolved itself, standing in for one whose SDK caches client-side.
   */
  private static final class NativelyCachingSecretStore implements LocallyCachedSecretStore {

    private final Map<String, String> held = new LinkedHashMap<>();

    @Override
    public Optional<String> lookupLocal(final String name) {
      return Optional.ofNullable(held.get(name));
    }

    @Override
    public Map<String, SecretResolutionResult> resolveFromStore(final Set<String> names) {
      return resultsFor(names, held);
    }

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      return resolveFromStore(names);
    }

    @Override
    public List<String> list() {
      return List.copyOf(held.keySet());
    }
  }
}
