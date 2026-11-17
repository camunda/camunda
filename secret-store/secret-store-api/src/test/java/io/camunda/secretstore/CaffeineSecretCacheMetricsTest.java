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
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheKeyNames;
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheResult;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** What {@link CaffeineSecretCache} publishes while it serves lookups and drops entries. */
final class CaffeineSecretCacheMetricsTest {

  private static final String STORE_ID = "store-a";
  private static final Duration TTL = Duration.ofMinutes(20);

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ControlledInstantSource timeSource =
      new ControlledInstantSource(Instant.parse("2026-01-01T00:00:00Z"));
  private final CaffeineSecretCache cache = meteredCache(STORE_ID);

  @Test
  void shouldCountALookupThatTheCacheAnswers() {
    // given
    cache.put("token", "secret-value");

    // when
    cache.get("token");

    // then
    assertThat(results(SecretCacheResult.HIT)).isOne();
    assertThat(results(SecretCacheResult.MISS)).isZero();
  }

  @Test
  void shouldCountALookupThatTheCacheCannotAnswer() {
    // when
    cache.get("token");

    // then
    assertThat(results(SecretCacheResult.MISS)).isOne();
    assertThat(results(SecretCacheResult.HIT)).isZero();
  }

  @Test
  void shouldCountEveryLookupExactlyOnce() {
    // given
    cache.put("token", "secret-value");

    // when the same name is looked up twice and an absent one once
    cache.get("token");
    cache.get("token");
    cache.get("absent");

    // then the hit rate is derivable, which is the only reason the two values share a meter
    assertThat(results(SecretCacheResult.HIT)).isEqualTo(2);
    assertThat(results(SecretCacheResult.MISS)).isOne();
  }

  @Test
  void shouldCountAnEntryDroppedBecauseItsTtlElapsed() {
    // given
    cache.put("token", "secret-value");

    // when the TTL elapses and pending maintenance runs — eviction is asynchronous, so nothing is
    // counted until Caffeine gets to it
    timeSource.advance(TTL);
    cache.cleanUp();

    // then
    assertThat(evictions(SecretCacheEvictionCause.EXPIRED)).isOne();
  }

  @Test
  void shouldCountEntriesDroppedBecauseTheCacheIsFull() {
    // given a cache bounded to a small size
    final var bounded =
        CaffeineSecretCache.create(
            10, TTL, timeSource, new SecretCacheMetrics(registry, "store-b"));

    // when more distinct names are written than the bound allows
    for (int i = 0; i < 100; i++) {
      bounded.put("secret-" + i, "value-" + i);
    }
    bounded.cleanUp();

    // then everything that did not fit is counted, so a working set larger than the bound is
    // visible as a rate rather than only as a low hit rate
    assertThat(evictions("store-b", SecretCacheEvictionCause.SIZE)).isEqualTo(90);
  }

  @Test
  void shouldCountAnEntryRemovedByName() {
    // given
    cache.put("token", "secret-value");

    // when
    cache.remove("token");

    // then Caffeine never reports this itself, so the cache counts it: it is how a secret the store
    // answered permanently — deleted, denied, or invalid — leaves the cache
    assertThat(evictions(SecretCacheEvictionCause.EXPLICIT)).isOne();
  }

  @Test
  void shouldNotCountRemovingANameThatWasNotCached() {
    // when
    cache.remove("never-cached");

    // then nothing was evicted. CachingSecretStore removes a name on every permanent failure from
    // its store, and most of those were never cached, so counting the calls would turn this series
    // into a count of failing lookups
    assertThat(evictions(SecretCacheEvictionCause.EXPLICIT)).isZero();
  }

  @Test
  void shouldNotCountRemovingANameAsALookup() {
    // given
    cache.put("token", "secret-value");

    // when
    cache.remove("token");

    // then the removal did not read the cache, and counting it as one would dilute the hit rate
    // with lookups no caller made
    assertThat(results(SecretCacheResult.HIT)).isZero();
    assertThat(results(SecretCacheResult.MISS)).isZero();
  }

  @Test
  void shouldNotCountOverwritingAValueAsAnEviction() {
    // given
    cache.put("token", "old-value");

    // when the same name is written again
    cache.put("token", "new-value");
    cache.cleanUp();

    // then nothing was evicted: the entry stayed and only its value changed, which the caching
    // store does on every re-resolve. Counting it would bury the causes that mean something
    assertThat(Arrays.stream(SecretCacheEvictionCause.values()).mapToDouble(this::evictions).sum())
        .isZero();
  }

  @Test
  void shouldPublishHowManyEntriesTheCacheHolds() {
    // given
    cache.put("token", "token-value");
    cache.put("apiKey", "api-key-value");

    // when
    cache.remove("token");

    // then
    assertThat(size(STORE_ID)).isOne();
  }

  @Test
  void shouldKeepTheMetersOfTwoStoresApart() {
    // given two caches on one registry, as one registry per physical tenant holds
    final var other =
        CaffeineSecretCache.create(
            10, TTL, timeSource, new SecretCacheMetrics(registry, "store-b"));
    cache.put("token", "value");

    // when each is looked up a different number of times
    cache.get("token");
    other.get("token");
    other.get("token");

    // then the store tag keeps them apart rather than letting one cache's numbers answer for the
    // other's
    assertThat(results(STORE_ID, SecretCacheResult.HIT)).isOne();
    assertThat(results("store-b", SecretCacheResult.MISS)).isEqualTo(2);
  }

  @Test
  void shouldPublishNothingWhenCreatedWithoutARegistry() {
    // given a cache created by a caller that named no registry
    final var unmetered = CaffeineSecretCache.create(10, TTL, timeSource);

    // when it is used
    unmetered.put("token", "value");
    unmetered.get("token");
    unmetered.remove("token");

    // then it registered nothing here — every meter on this registry still belongs to the cache
    // that named it. A caller that does not want metrics must not have to name a registry, and must
    // not end up publishing into someone else's
    assertThat(registry.getMeters())
        .isNotEmpty()
        .allSatisfy(
            meter ->
                assertThat(meter.getId().getTag(SecretCacheKeyNames.STORE.asString()))
                    .isEqualTo(STORE_ID));
  }

  @Test
  void shouldNotTagAnyMeterWithASecretNameOrValue() {
    // given a cache exercised through every path that could tag a meter
    cache.put("api-token", "sk-live-1234");
    cache.get("api-token");
    cache.get("other-secret");
    cache.remove("api-token");
    timeSource.advance(TTL);
    cache.cleanUp();

    // when
    final var tagValues =
        registry.getMeters().stream()
            .flatMap(meter -> meter.getId().getTags().stream())
            .map(Tag::getValue)
            .toList();

    // then neither the name nor the value of a secret reaches the metrics endpoint. The cache is
    // keyed by the bare secret name, so such a tag would be unbounded cardinality and customer data
    // at once
    assertThat(tagValues).doesNotContain("api-token", "other-secret", "sk-live-1234");
  }

  private double results(final SecretCacheResult result) {
    return results(STORE_ID, result);
  }

  private double results(final String storeId, final SecretCacheResult result) {
    return SecretCacheMeters.results(registry, storeId, result);
  }

  private double evictions(final SecretCacheEvictionCause cause) {
    return evictions(STORE_ID, cause);
  }

  private double evictions(final String storeId, final SecretCacheEvictionCause cause) {
    return SecretCacheMeters.evictions(registry, storeId, cause);
  }

  private double size(final String storeId) {
    return SecretCacheMeters.size(registry, storeId);
  }

  private CaffeineSecretCache meteredCache(final String storeId) {
    return CaffeineSecretCache.create(
        CaffeineSecretCache.DEFAULT_MAX_SIZE,
        TTL,
        timeSource,
        new SecretCacheMetrics(registry, storeId));
  }
}
