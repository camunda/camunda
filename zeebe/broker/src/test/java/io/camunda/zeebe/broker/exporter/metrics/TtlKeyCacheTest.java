/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.test.util.junit.RegressionTest;
import org.junit.jupiter.api.Test;

final class TtlKeyCacheTest {

  @Test
  void shouldStorePair() {
    // given
    final var cache = new TtlKeyCache();

    // when
    cache.store(1L, 10L);
    final var timestamp = cache.remove(1L);

    // then
    assertThat(timestamp).isEqualTo(10L);
  }

  @Test
  void shouldStoreMultipleKeysWithSameTimestamp() {
    // given
    final var cache = new TtlKeyCache();
    cache.store(1L, 10L);
    cache.store(2L, 10L);

    // when - then
    assertThat(cache.remove(1L)).isEqualTo(10L);
    assertThat(cache.remove(2L)).isEqualTo(10L);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/16405")
  void shouldCleanupKeysEvenWithSameTimestamps() {
    // given
    final var cache = new TtlKeyCache();
    cache.store(1L, 10L);
    cache.store(2L, 10L);

    // when
    cache.cleanup(11L);

    // when - then
    assertThat(cache.isEmpty()).isTrue();
  }

  @Test
  void shouldRemovePair() {
    // given
    final var cache = new TtlKeyCache();
    cache.store(1L, 10L);

    // when
    cache.remove(1L);

    // then
    assertThat(cache.isEmpty()).isTrue();
  }

  @Test
  void shouldCleanupExpiredEntries() {
    // given
    final var cache = new TtlKeyCache();
    cache.store(1L, 10L);
    cache.store(2L, 20L);
    cache.store(3L, 30L);

    // when
    cache.cleanup(21L);

    // then - assert we can still find the non expired key, then once removed, the cache is empty
    assertThat(cache.remove(3L)).isEqualTo(30L);
    assertThat(cache.isEmpty()).isTrue();
  }

  @Test
  void shouldClearCache() {
    // given
    final var cache = new TtlKeyCache();
    cache.store(1L, 10L);
    cache.store(2L, 20L);
    cache.store(3L, 30L);

    // when
    cache.clear();

    // then
    assertThat(cache.isEmpty()).isTrue();
  }

  @Test
  void shouldEvictOldestKeyOnCapacityReached() {
    // given
    final var cache = new TtlKeyCache(3);
    cache.store(1L, 10L);
    cache.store(2L, 20L);
    cache.store(3L, 30L);

    // when
    cache.store(4L, 40L);

    // then
    assertThat(cache.size()).isEqualTo(3);
    assertThat(cache.remove(1L)).isEqualTo(-1L);
    assertThat(cache.remove(2L)).isEqualTo(20L);
    assertThat(cache.remove(3L)).isEqualTo(30L);
    assertThat(cache.remove(4L)).isEqualTo(40L);
  }

  @Test
  void shouldReturnNullValue() {
    // given
    final var cache = new TtlKeyCache(3L);
    cache.store(1L, 10L);

    // when
    final var timestamp = cache.remove(2L);

    // then
    assertThat(timestamp).isEqualTo(3L);
  }

  @Test
  void shouldGetTimestampByKey() {
    // given
    final var cache = new TtlKeyCache();
    cache.store(1L, 10L);

    // when
    final var timestamp = cache.get(1L);

    // then
    assertThat(timestamp).isEqualTo(10L);
    assertThat(cache.get(1L)).isEqualTo(10L); // assert it's still there
    assertThat(cache.size()).isOne();
  }
}
