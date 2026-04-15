/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExporterEntityCacheImplTest {

  @Test
  void shouldLoadSingleValue() {
    // given
    final var cache =
        new ExporterEntityCacheImpl<>(
            10,
            new CacheLoader<Integer, String>() {
              @Override
              public String load(final Integer key) {
                return "value-" + key;
              }
            },
            newStatsCounter());

    // when
    final var value = cache.get(1);

    // then
    assertThat(value).contains("value-1");
  }

  @Test
  void shouldUseBulkLoaderForGetAll() {
    // given
    final var singleLoadCalls = new AtomicInteger();
    final var bulkLoadCalls = new AtomicInteger();
    final var cache =
        new ExporterEntityCacheImpl<>(
            10,
            new BulkExporterEntityCacheLoader<Integer, String>() {
              @Override
              public String load(final Integer key) {
                singleLoadCalls.incrementAndGet();
                return "value-" + key;
              }

              @Override
              public Map<? extends Integer, ? extends String> loadAll(
                  final Set<? extends Integer> keys) {
                bulkLoadCalls.incrementAndGet();
                return keys.stream()
                    .collect(java.util.stream.Collectors.toMap(k -> k, k -> "value-" + k));
              }
            },
            newStatsCounter());

    // when
    final var values = cache.getAll(List.of(1, 2));

    // then
    assertThat(values).containsExactlyInAnyOrderEntriesOf(Map.of(1, "value-1", 2, "value-2"));
    assertThat(singleLoadCalls).hasValue(0);
    assertThat(bulkLoadCalls).hasValue(1);
  }

  @Test
  void shouldFallbackToSingleLoadsWhenBulkLoadingIsNotSupported() {
    // given
    final var singleLoadCalls = new AtomicInteger();
    final var cache =
        new ExporterEntityCacheImpl<>(
            10,
            new CacheLoader<Integer, String>() {
              @Override
              public String load(final Integer key) {
                singleLoadCalls.incrementAndGet();
                return "value-" + key;
              }
            },
            newStatsCounter());

    // when
    final var values = cache.getAll(List.of(1, 2));

    // then
    assertThat(values).containsExactlyInAnyOrderEntriesOf(Map.of(1, "value-1", 2, "value-2"));
    assertThat(singleLoadCalls).hasValue(2);
  }

  @Test
  void shouldWrapSingleLoadFailures() {
    // given
    final var cache =
        new ExporterEntityCacheImpl<>(
            10,
            new CacheLoader<Integer, String>() {
              @Override
              public String load(final Integer key) {
                throw new IllegalStateException("single failure");
              }
            },
            newStatsCounter());

    // when then
    assertThatThrownBy(() -> cache.get(1))
        .isInstanceOf(ExporterEntityCache.CacheLoaderFailedException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("single failure");
  }

  @Test
  void shouldWrapBulkLoadFailures() {
    // given
    final var cache =
        new ExporterEntityCacheImpl<>(
            10,
            new BulkExporterEntityCacheLoader<Integer, String>() {
              @Override
              public String load(final Integer key) {
                return "value-" + key;
              }

              @Override
              public Map<? extends Integer, ? extends String> loadAll(
                  final Set<? extends Integer> keys) {
                throw new IllegalStateException("bulk failure");
              }
            },
            newStatsCounter());

    // when then
    assertThatThrownBy(() -> cache.getAll(List.of(1, 2)))
        .isInstanceOf(ExporterEntityCache.CacheLoaderFailedException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("bulk failure");
  }

  private static CaffeineCacheStatsCounter newStatsCounter() {
    return new CaffeineCacheStatsCounter(
        "camunda.test.exporter.cache", "entity", new SimpleMeterRegistry());
  }
}
