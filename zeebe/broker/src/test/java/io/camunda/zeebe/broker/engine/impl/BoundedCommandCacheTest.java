/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.engine.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.agrona.collections.LongHashSet;
import org.junit.jupiter.api.Test;

final class BoundedCommandCacheTest {
  @Test
  void shouldNotExceedCapacity() {
    // given
    final var cache = new BoundedCommandCache(4);
    cache.addAll(setOf(1, 2, 3, 4));

    // when
    cache.addAll(setOf(5, 6));

    // then
    assertThat(cache.size()).isEqualTo(4);
    assertThat(cache.contains(5)).isTrue();
    assertThat(cache.contains(6)).isTrue();
  }

  @Test
  void shouldEvictRandomKeysOnCapacityReached() {
    // given
    final var cache = new BoundedCommandCache(4);
    final var initialKeys = setOf(1, 2, 3, 4);
    cache.addAll(initialKeys);

    // when
    cache.addAll(setOf(5, 6));

    // then
    final var remainingInitialKeys =
        initialKeys.stream().filter(cache::contains).collect(Collectors.toSet());
    assertThat(remainingInitialKeys).hasSize(2).containsAnyElementsOf(initialKeys);
  }

  @Test
  void shouldReportSizeChanges() {
    // given
    final var reportedSize = new AtomicInteger();
    final var cache = new BoundedCommandCache(reportedSize::set);

    // when - then
    cache.addAll(setOf(1, 2, 3, 4));
    assertThat(reportedSize).hasValue(4);

    // when - then
    cache.remove(1);
    assertThat(reportedSize).hasValue(3);
  }

  @Test
  void shouldReportSizeToResetMetricOnCreation() {
    // given
    final var reportedSize = new AtomicInteger(200);

    // when
    final var ignored = new BoundedCommandCache(reportedSize::set);

    // then
    assertThat(reportedSize).hasValue(0);
  }

  @Test
  void shouldClearCache() {
    // given
    final var reportedSize = new AtomicInteger();
    final var cache = new BoundedCommandCache(reportedSize::set);
    cache.addAll(setOf(1, 2, 3, 4));

    // when
    cache.clear();

    // then
    assertThat(cache.size()).isZero();
    assertThat(reportedSize).hasValue(0);
  }

  private LongHashSet setOf(final long... keys) {
    final var set = new LongHashSet();
    set.addAll(Arrays.stream(keys).boxed().toList());
    return set;
  }
}
