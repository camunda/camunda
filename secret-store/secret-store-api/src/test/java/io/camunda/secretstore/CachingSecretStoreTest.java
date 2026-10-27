/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheEvictionCause;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CachingSecretStoreTest {

  private static final String STORE_ID = "default";

  private final RecordingSecretStore store = new RecordingSecretStore();
  private final InMemorySecretCache cache = new InMemorySecretCache();
  private final CachingSecretStore cachingStore = new CachingSecretStore(store, cache);

  @Test
  void shouldServeACachedNameWithoutAskingTheStore() {
    // given
    cache.put("token", "cached-value");

    // when
    final var results = cachingStore.resolve(Set.of("token"));

    // then
    assertThat(results.get("token")).isEqualTo(new Resolved("cached-value"));
    assertThat(store.resolveCalls).isEmpty();
  }

  @Test
  void shouldOnlyAskTheStoreForUncachedNames() {
    // given one of two names is cached
    store.holds("b", "b-value");
    cache.put("a", "cached-a-value");

    // when
    final var results = cachingStore.resolve(Set.of("a", "b"));

    // then only the miss reaches the store, and both names are still answered
    assertThat(store.resolveCalls).containsExactly(Set.of("b"));
    assertThat(results)
        .containsEntry("a", new Resolved("cached-a-value"))
        .containsEntry("b", new Resolved("b-value"));
  }

  @Test
  void shouldCacheAValueReadFromTheStore() {
    // given
    store.holds("token", "token-value");

    // when the same name is resolved twice
    cachingStore.resolve(Set.of("token"));
    final var second = cachingStore.resolve(Set.of("token"));

    // then the store was read once and the cached value answers the second call
    assertThat(store.resolveCalls).containsExactly(Set.of("token"));
    assertThat(second.get("token")).isEqualTo(new Resolved("token-value"));
    assertThat(cache.get("token")).contains("token-value");
  }

  @Test
  void shouldNotCacheAFailure() {
    // given a name the store cannot read
    store.fails("token", SecretErrorCode.UNREADABLE);

    // when the same name is resolved twice
    cachingStore.resolve(Set.of("token"));
    cachingStore.resolve(Set.of("token"));

    // then the store is asked again: caching the failure would keep the secret unresolvable until
    // the process restarts
    assertThat(store.resolveCalls).containsExactly(Set.of("token"), Set.of("token"));
    assertThat(cache.get("token")).isEmpty();
  }

  @Test
  void shouldLeaveOutANameTheStoreDidNotAnswerFor() {
    // given a store that answers without a result for the name it was asked for
    store.omitsResults();

    // when
    final var results = cachingStore.resolve(Set.of("token"));

    // then the name is absent rather than made up into a failure, so a caller can still tell an
    // unanswered name apart from a failed one
    assertThat(results).doesNotContainKey("token");
  }

  @Test
  void shouldListFromTheStoreRatherThanTheCache() {
    // given a cached value the store no longer holds
    store.holds("token", "token-value");
    cache.put("ghost", "cached-value");

    // when
    final var names = cachingStore.list();

    // then the listing is the store's own: a cache holds the values read so far, not the store's
    // set of secrets
    assertThat(names).containsExactly("token");
  }

  @Test
  void shouldServeALocalLookupFromTheCacheWithoutReadingTheStore() {
    // given
    store.holds("token", "token-value");
    cache.put("token", "cached-value");

    // when
    final var local = cachingStore.lookupLocal("token");

    // then the store is never read: a local lookup runs where blocking on store I/O is not allowed
    assertThat(local).contains("cached-value");
    assertThat(store.resolveCalls).isEmpty();
  }

  @Test
  void shouldNotServeALocalLookupForAnUncachedName() {
    // given a name only the store holds
    store.holds("token", "token-value");

    // when
    final var local = cachingStore.lookupLocal("token");

    // then it is reported as not held locally rather than read from the store
    assertThat(local).isEmpty();
    assertThat(store.resolveCalls).isEmpty();
  }

  @Test
  void shouldServeALocalLookupWithAValueReadFromTheStore() {
    // given a name resolved once
    store.holds("token", "token-value");
    cachingStore.resolve(Set.of("token"));

    // when
    final var local = cachingStore.lookupLocal("token");

    // then what a resolution read is what a later local lookup sees, so a background resolution
    // can populate what a non-blocking caller reads
    assertThat(local).contains("token-value");
  }

  @Test
  void shouldNotServeALocalLookupWithAFailedResolution() {
    // given a name the store cannot read
    store.fails("token", SecretErrorCode.UNREADABLE);
    cachingStore.resolve(Set.of("token"));

    // when / then a failure is not cached, so nothing is held locally for it either
    assertThat(cachingStore.lookupLocal("token")).isEmpty();
  }

  @Test
  void shouldAskTheStoreForACachedNameWhenResolvingFromTheStore() {
    // given a cached value the store no longer holds
    cache.put("token", "cached-value");
    store.fails("token", SecretErrorCode.NOT_FOUND);

    // when
    final var results = cachingStore.resolveFromStore(Set.of("token"));

    // then the store is asked and its answer wins over the cached one: the caller records a durable
    // outcome, so it must not report a secret the store no longer has as resolved
    assertThat(store.resolveCalls).containsExactly(Set.of("token"));
    assertThat(results.get("token"))
        .isEqualTo(new Failed(SecretErrorCode.NOT_FOUND, "failed: token", null));
    assertThat(cachingStore.lookupLocal("token")).isEmpty();
  }

  @Test
  void shouldInvalidateACachedValueWhenResolvingFromTheStoreFails() {
    // given a cached value that the store now fails authoritatively
    cache.put("token", "cached-value");
    store.fails("token", SecretErrorCode.ACCESS_DENIED);

    // when
    cachingStore.resolveFromStore(Set.of("token"));

    // then the stale value is no longer available to cache-only readers
    assertThat(cachingStore.lookupLocal("token")).isEmpty();
  }

  @Test
  void shouldInvalidateOnlyFailedNamesInABatchResolvedFromTheStore() {
    // given two cached values, where the store now resolves one and rejects the other
    cache.put("token", "old-token");
    cache.put("apiKey", "old-api-key");
    store.holds("token", "new-token");
    store.fails("apiKey", SecretErrorCode.ACCESS_DENIED);

    // when
    final var results = cachingStore.resolveFromStore(Set.of("token", "apiKey"));

    // then the successful result refreshes its cache entry, and the failed result clears only its
    // stale value
    assertThat(results)
        .containsEntry("token", new Resolved("new-token"))
        .containsEntry("apiKey", new Failed(SecretErrorCode.ACCESS_DENIED, "failed: apiKey", null));
    assertThat(cachingStore.lookupLocal("token")).contains("new-token");
    assertThat(cachingStore.lookupLocal("apiKey")).isEmpty();
  }

  @Test
  void shouldCacheAValueResolvedFromTheStore() {
    // given
    store.holds("token", "token-value");

    // when
    cachingStore.resolveFromStore(Set.of("token"));

    // then a resolve bypassing the cache still populates it, which is what makes the value
    // available to a later cache-only lookup
    assertThat(cachingStore.lookupLocal("token")).contains("token-value");
    assertThat(cache.get("token")).contains("token-value");
  }

  @Test
  void shouldNotCacheAFailureResolvedFromTheStore() {
    // given a name the store cannot read
    store.fails("token", SecretErrorCode.UNREADABLE);

    // when
    cachingStore.resolveFromStore(Set.of("token"));

    // then nothing is cached for it, so the next resolution retries rather than serving the failure
    assertThat(cache.get("token")).isEmpty();
  }

  @Test
  void shouldKeepAStaleCachedValueWhenTheStoreFailsWithATransientError() {
    // given a cached value the store can no longer read due to a transient error
    cache.put("token", "cached-value");
    store.fails("token", SecretErrorCode.UNREADABLE);

    // when
    cachingStore.resolveFromStore(Set.of("token"));

    // then the stale value stays available rather than being dropped over a read blip
    assertThat(cachingStore.lookupLocal("token")).contains("cached-value");
  }

  @Test
  void shouldAnswerATypeCheckForTheStoreItWraps() {
    // when / then the wrapper answers for the store it caches for, so a caller can tell which store
    // was configured whether or not it had to be wrapped
    assertThat(cachingStore.is(RecordingSecretStore.class)).isTrue();
  }

  @Test
  void shouldNotAnswerATypeCheckForAnotherStoreType() {
    // when / then
    assertThat(cachingStore.is(NoopSecretStore.class)).isFalse();
  }

  @Test
  void shouldNotAnswerATypeCheckForItself() {
    // when / then the type check is about the configured store, not about how it is held: a caller
    // asserting a store type must not have to know whether it is wrapped
    assertThat(cachingStore.is(CachingSecretStore.class)).isFalse();
  }

  @Test
  void shouldAnswerATypeCheckForAStoreThatIsNotWrapped() {
    // when / then an unwrapped store answers for itself, so the same check serves both
    assertThat(store.is(RecordingSecretStore.class)).isTrue();
  }

  @Test
  void shouldResolveARotatedValueOnceTheTtlElapses() {
    // given a store whose value changes, cached through a TTL-bounded cache instead of the
    // never-expiring in-memory fake the other tests use
    final var timeSource = new ControlledInstantSource(Instant.parse("2026-01-01T00:00:00Z"));
    final var ttl = Duration.ofMinutes(20);
    final var ttlCache =
        CaffeineSecretCache.create(CaffeineSecretCache.DEFAULT_MAX_SIZE, ttl, timeSource);
    final var ttlStore = new CachingSecretStore(store, ttlCache);
    store.holds("token", "v1");

    // when resolved once, the store is read and the value cached
    assertThat(ttlStore.resolve(Set.of("token")).get("token")).isEqualTo(new Resolved("v1"));
    assertThat(store.resolveCalls).containsExactly(Set.of("token"));

    // and when the store's value changes before the TTL elapses
    store.holds("token", "v2");
    ttlStore.resolve(Set.of("token"));

    // then the cache still answers with the stale value, and the store was not asked again
    assertThat(ttlStore.resolve(Set.of("token")).get("token")).isEqualTo(new Resolved("v1"));
    assertThat(store.resolveCalls).containsExactly(Set.of("token"));

    // when the TTL elapses
    timeSource.advance(ttl);

    // then the next resolution reads the store again and picks up the rotated value, with no
    // restart required
    assertThat(ttlStore.resolve(Set.of("token")).get("token")).isEqualTo(new Resolved("v2"));
    assertThat(store.resolveCalls).containsExactly(Set.of("token"), Set.of("token"));
  }

  @Test
  void shouldCountInvalidatingACachedValueAsAnEviction() {
    // given a cached value that the store now fails authoritatively, held in a metered cache
    // instead of the in-memory fake the other tests use
    final var registry = new SimpleMeterRegistry();
    final var meteredStore = new CachingSecretStore(store, meteredCache(registry));
    store.holds("token", "cached-value");
    meteredStore.resolve(Set.of("token"));
    store.fails("token", SecretErrorCode.ACCESS_DENIED);

    // when
    meteredStore.resolveFromStore(Set.of("token"));

    // then dropping a secret the store no longer serves is visible as an eviction, which is what
    // tells that case apart from a cache that simply ran out of room
    assertThat(evictions(registry, SecretCacheEvictionCause.EXPLICIT)).isOne();
  }

  @Test
  void shouldNotCountAPermanentFailureForANameThatWasNotCached() {
    // given a name that was never resolved, so nothing is held for it
    final var registry = new SimpleMeterRegistry();
    final var meteredStore = new CachingSecretStore(store, meteredCache(registry));
    store.fails("token", SecretErrorCode.NOT_FOUND);

    // when
    meteredStore.resolveFromStore(Set.of("token"));

    // then nothing was evicted: a permanent failure is attempted for every such name, and counting
    // the attempts would turn the eviction series into a count of failing lookups
    assertThat(evictions(registry, SecretCacheEvictionCause.EXPLICIT)).isZero();
  }

  @Test
  void shouldNotCountATransientFailureAsAnEviction() {
    // given a cached value the store can no longer read due to a transient error
    final var registry = new SimpleMeterRegistry();
    final var meteredStore = new CachingSecretStore(store, meteredCache(registry));
    store.holds("token", "cached-value");
    meteredStore.resolve(Set.of("token"));
    store.fails("token", SecretErrorCode.UNREADABLE);

    // when
    meteredStore.resolveFromStore(Set.of("token"));

    // then the stale value stays, so nothing left the cache to count
    assertThat(meteredStore.lookupLocal("token")).contains("cached-value");
    assertThat(evictions(registry, SecretCacheEvictionCause.EXPLICIT)).isZero();
  }

  private static CaffeineSecretCache meteredCache(final SimpleMeterRegistry registry) {
    return CaffeineSecretCache.create(
        CaffeineSecretCache.DEFAULT_MAX_SIZE,
        Duration.ofMinutes(20),
        new ControlledInstantSource(Instant.parse("2026-01-01T00:00:00Z")),
        new SecretCacheMetrics(registry, STORE_ID));
  }

  private static double evictions(
      final SimpleMeterRegistry registry, final SecretCacheEvictionCause cause) {
    return SecretCacheMeters.evictions(registry, STORE_ID, cause);
  }

  private static final class RecordingSecretStore implements SecretStore {

    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, SecretErrorCode> failures = new LinkedHashMap<>();
    private final List<Set<String>> resolveCalls = new ArrayList<>();
    private boolean omitResults;

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      resolveCalls.add(Set.copyOf(names));
      final Map<String, SecretResolutionResult> results = new LinkedHashMap<>();
      if (omitResults) {
        return results;
      }
      names.forEach(name -> results.put(name, resultFor(name)));
      return results;
    }

    @Override
    public List<String> list() {
      return List.copyOf(values.keySet());
    }

    void holds(final String name, final String value) {
      values.put(name, value);
    }

    void fails(final String name, final SecretErrorCode code) {
      failures.put(name, code);
    }

    void omitsResults() {
      omitResults = true;
    }

    private SecretResolutionResult resultFor(final String name) {
      final var failure = failures.get(name);
      if (failure != null) {
        return new Failed(failure, "failed: " + name, null);
      }
      final var value = values.get(name);
      return value == null
          ? new Failed(SecretErrorCode.NOT_FOUND, "no secret found: " + name, null)
          : new Resolved(value);
    }
  }
}
