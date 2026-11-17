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

import com.github.benmanes.caffeine.cache.RemovalCause;
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheEvictionCause;
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheKeyNames;
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheResult;
import io.micrometer.core.instrument.Meter.Type;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The doc enum is the documentation for these meters, so it is worth asserting on. Renaming a meter
 * or a tag key silently breaks every dashboard and alert built on it, and there is no repo-wide
 * check for that.
 */
final class SecretCacheMetricsDocTest {

  @Test
  void shouldBeNamedAndDocumentedConsistently() {
    // given
    final var meters = SecretCacheMetricsDoc.values();

    // when/then
    assertThat(meters)
        .isNotEmpty()
        .allSatisfy(
            meter -> {
              assertThat(meter.getName())
                  .startsWith("camunda.secret.cache.")
                  .doesNotContain("..")
                  .doesNotContain("-")
                  .doesNotContain("_");
              assertThat(meter.getDescription()).isNotBlank();
              // every meter belongs to exactly one store's cache, and nothing else distinguishes
              // two caches from one another
              assertThat(meter.getKeyNames()).contains(SecretCacheKeyNames.STORE);
            });
  }

  @Test
  void shouldDocumentTheResultAsACounterTaggedByStoreAndResult() {
    // given
    final var meter = SecretCacheMetricsDoc.CACHE_RESULT;

    // when/then
    assertThat(meter.getName()).isEqualTo("camunda.secret.cache.result");
    assertThat(meter.getType()).isEqualTo(Type.COUNTER);
    assertThat(meter.getKeyNames())
        .containsExactly(SecretCacheKeyNames.STORE, SecretCacheKeyNames.RESULT);
    // the hit rate is derived rather than published, which only works if both values count the
    // same thing — one lookup
    assertThat(SecretCacheResult.values())
        .extracting(Enum::name)
        .containsExactlyInAnyOrder("HIT", "MISS");
  }

  @Test
  void shouldPointAtTheResolutionOutcomeForMissesNoTuningCanFix() {
    // given/when/then — a reference the store answers permanently is never cached, so it misses on
    // every lookup. Without the cross-reference an operator reads that as a cache to tune and
    // reaches for a TTL or a maximum that cannot move it
    assertThat(SecretCacheMetricsDoc.CACHE_RESULT.getDescription())
        .contains("camunda.secret.resolution.outcome");
  }

  @Test
  void shouldDocumentTheEvictionsAsACounterTaggedByStoreAndCause() {
    // given
    final var meter = SecretCacheMetricsDoc.CACHE_EVICTIONS;

    // when/then
    assertThat(meter.getName()).isEqualTo("camunda.secret.cache.evictions");
    assertThat(meter.getType()).isEqualTo(Type.COUNTER);
    assertThat(meter.getKeyNames())
        .containsExactly(SecretCacheKeyNames.STORE, SecretCacheKeyNames.CAUSE);
  }

  @Test
  void shouldDocumentTheSizeAsAGaugeWithoutABaseUnit() {
    // given
    final var meter = SecretCacheMetricsDoc.CACHE_SIZE;

    // when/then — Micrometer appends the base unit to the meter name, so declaring one here would
    // publish camunda_secret_cache_size_entries instead of the documented name
    assertThat(meter.getName()).isEqualTo("camunda.secret.cache.size");
    assertThat(meter.getType()).isEqualTo(Type.GAUGE);
    assertThat(meter.getBaseUnit()).isNull();
    assertThat(meter.getKeyNames()).containsExactly(SecretCacheKeyNames.STORE);
  }

  @Test
  void shouldStateTheBoundsAndNameThePropertiesThatTuneThem() {
    // given/when/then — neither bound is published on a meter of its own, so these descriptions
    // are the only place an operator reads them, and both are configurable: without the property
    // path, a reader watching a sustained SIZE or EXPIRED rate has nothing to act on. Asserting
    // the live constants rather than literals keeps a changed default from making the stated
    // values lie
    assertThat(SecretCacheMetricsDoc.CACHE_SIZE.getDescription())
        .contains(String.valueOf(CaffeineSecretCache.DEFAULT_MAX_SIZE))
        .contains("camunda.secrets.cache.max-size");
    assertThat(SecretCacheMetricsDoc.CACHE_EVICTIONS.getDescription())
        .contains(String.valueOf(CaffeineSecretCache.DEFAULT_MAX_SIZE))
        .contains(String.valueOf(CaffeineSecretCache.DEFAULT_TTL.toMinutes()))
        .contains("camunda.secrets.cache.max-size")
        .contains("camunda.secrets.cache.ttl");
  }

  @Test
  void shouldDocumentThatTheSizeIsAnEstimate() {
    // given/when/then — eviction is asynchronous, so a reader who takes the value as exact will
    // see it sit above the configured maximum and call it a bug
    assertThat(SecretCacheMetricsDoc.CACHE_SIZE.getDescription()).contains("Estimated");
  }

  @Test
  void shouldCountOneCacheEntryPerEvictionCauseValue() {
    // given/when/then — all four values count one entry leaving one cache, so they share a unit
    // and can be summed across the tag. A quantity measured per anything else would have to go on
    // its own meter
    assertThat(SecretCacheEvictionCause.values())
        .extracting(Enum::name)
        .containsExactlyInAnyOrder("SIZE", "EXPIRED", "EXPLICIT", "COLLECTED");
  }

  @Test
  void shouldMapEveryEvictingRemovalCauseToItsOwnValue() {
    // given every cause Caffeine reports as an eviction
    final var evicting = Stream.of(RemovalCause.values()).filter(RemovalCause::wasEvicted).toList();

    // when
    final var causes = evicting.stream().map(SecretCacheEvictionCause::from).toList();

    // then no two of them are folded onto one tag value, which would make a cache that is too small
    // indistinguishable from one whose TTL is too short
    assertThat(causes).doesNotHaveDuplicates().hasSameSizeAs(evicting);
  }

  @Test
  void shouldMapARemovalByNameToExplicit() {
    // when/then — Caffeine reports this cause to no StatsCounter
    // (RemovalCause.EXPLICIT.wasEvicted()
    // is false), so CaffeineSecretCache.remove counts it itself and nothing else exercises this
    // arm. Without this it could be mapped to any other cause and every test would still pass
    assertThat(SecretCacheEvictionCause.from(RemovalCause.EXPLICIT))
        .isEqualTo(SecretCacheEvictionCause.EXPLICIT);
  }

  @Test
  void shouldRejectAReplacedValueAsAnEviction() {
    // when/then — the entry stays and only its value changes, which CachingSecretStore does on
    // every re-resolve. Folding it into a cause would bury the evictions that mean something
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SecretCacheEvictionCause.from(RemovalCause.REPLACED))
        .withMessageContaining("REPLACED");
  }

  @Test
  void shouldUseTheSameStoreTagKeyAsTheResolutionMeters() {
    // when/then — the engine's camunda.secret.resolution.* meters tag `store` too, and a dashboard
    // correlating a low hit rate with slow resolutions has to be able to join on it
    assertThat(SecretCacheKeyNames.STORE.asString()).isEqualTo("store");
  }
}
