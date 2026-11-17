/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class CaffeineSecretCacheTest {

  private final ControlledInstantSource timeSource =
      new ControlledInstantSource(Instant.parse("2026-01-01T00:00:00Z"));
  private final CaffeineSecretCache cache =
      CaffeineSecretCache.create(
          CaffeineSecretCache.DEFAULT_MAX_SIZE, Duration.ofMinutes(20), timeSource);

  @Test
  void shouldServeAValueBeforeTheTtlElapses() {
    // given
    cache.put("token", "secret-value");
    timeSource.advance(Duration.ofMinutes(19));

    // when / then
    assertThat(cache.get("token")).contains("secret-value");
  }

  @Test
  void shouldMissAfterTheTtlElapses() {
    // given
    cache.put("token", "secret-value");

    // when the TTL elapses
    timeSource.advance(Duration.ofMinutes(20));

    // then the next lookup misses, without a cleanUp() call: Caffeine checks expiry against the
    // ticker on the read path itself
    assertThat(cache.get("token")).isEmpty();
  }

  @Test
  void shouldNotExpireJustBeforeTheTtlElapses() {
    // given
    cache.put("token", "secret-value");

    // when one millisecond short of the TTL
    timeSource.advance(Duration.ofMinutes(20).minusMillis(1));

    // then still present, pinning the boundary against an off-by-one
    assertThat(cache.get("token")).contains("secret-value");
  }

  @Test
  void shouldNotGrowBeyondTheMaximumSize() {
    // given a cache bounded to a small size
    final var bounded = CaffeineSecretCache.create(10, Duration.ofMinutes(20), timeSource);

    // when more distinct names are written than the bound allows
    for (int i = 0; i < 100; i++) {
      bounded.put("secret-" + i, "value-" + i);
    }
    // Caffeine evicts asynchronously; force maintenance rather than asserting on an exact count
    // immediately
    bounded.cleanUp();

    // then no more than the bound survived
    final var survivors =
        (int) IntStream.range(0, 100).filter(i -> bounded.get("secret-" + i).isPresent()).count();
    assertThat(survivors).isEqualTo(10);
  }

  @Test
  void shouldOverwriteValueOnSecondPut() {
    // given
    cache.put("token", "old-value");

    // when
    cache.put("token", "new-value");

    // then
    assertThat(cache.get("token")).contains("new-value");
  }

  @Test
  void shouldRemoveAValue() {
    // given
    cache.put("token", "secret-value");

    // when
    cache.remove("token");

    // then
    assertThat(cache.get("token")).isEmpty();
  }

  @Test
  void shouldIsolateDistinctReferences() {
    // given
    cache.put("token", "token-value");
    cache.put("apiKey", "api-key-value");

    // when / then
    assertThat(cache.get("token")).contains("token-value");
    assertThat(cache.get("apiKey")).contains("api-key-value");
    assertThat(cache.get("unknown")).isEmpty();
  }

  @Test
  void shouldRejectNullValue() {
    // when / then — a resolved value is never null; Caffeine guards against it the same way
    // InMemorySecretCache's ConcurrentHashMap does
    assertThatThrownBy(() -> cache.put("token", null)).isInstanceOf(NullPointerException.class);
  }
}
