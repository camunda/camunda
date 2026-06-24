/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.engine.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class BoundedScheduledCommandCacheTest {

  private static final ScheduledCommandCacheMetrics NOOP_METRICS = ignored -> i -> {};

  @Test
  void shouldNotCacheUnknownIntents() {
    // given
    final var cache = BoundedScheduledCommandCache.ofIntent(NOOP_METRICS, TimerIntent.TRIGGER);

    // when
    cache.add(JobIntent.TIME_OUT, 1);

    // then
    assertThat(cache.contains(JobIntent.TIME_OUT, 1)).isFalse();
  }

  @Test
  void shouldAdd() {
    // given
    final var cache = BoundedScheduledCommandCache.ofIntent(NOOP_METRICS, TimerIntent.TRIGGER);

    // when
    cache.add(TimerIntent.TRIGGER, 1);

    // then
    assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isTrue();
  }

  @Test
  void shouldNotContainNonCachedKeyIntentPairWithSameIntent() {
    // given
    final var cache = BoundedScheduledCommandCache.ofIntent(NOOP_METRICS, TimerIntent.TRIGGER);

    // when
    cache.add(TimerIntent.TRIGGER, 1);

    // then
    assertThat(cache.contains(TimerIntent.TRIGGER, 2)).isFalse();
  }

  @Test
  void shouldNotContainNonCachedKeyIntentPairWithSameKey() {
    // given
    final var cache =
        BoundedScheduledCommandCache.ofIntent(
            NOOP_METRICS, TimerIntent.TRIGGER, JobIntent.TIME_OUT);

    // when
    cache.add(JobIntent.TIME_OUT, 1);

    // then
    assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isFalse();
  }

  @Test
  void shouldRemoveCachedIntentKeyPair() {
    // given
    final var cache =
        BoundedScheduledCommandCache.ofIntent(
            NOOP_METRICS, TimerIntent.TRIGGER, JobIntent.TIME_OUT);
    cache.add(JobIntent.TIME_OUT, 1);
    cache.add(TimerIntent.TRIGGER, 1);

    // when
    cache.remove(JobIntent.TIME_OUT, 1);

    // then
    assertThat(cache.contains(JobIntent.TIME_OUT, 1)).isFalse();
    assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isTrue();
  }

  @Test
  void shouldReportSizePerIntent() {
    // given
    final Map<Intent, AtomicInteger> metrics = new HashMap<>();
    final var cache =
        BoundedScheduledCommandCache.ofIntent(
            i -> metrics.computeIfAbsent(i, ignored -> new AtomicInteger())::set,
            TimerIntent.TRIGGER,
            JobIntent.TIME_OUT);

    // when
    cache.add(JobIntent.TIME_OUT, 1);
    cache.add(TimerIntent.TRIGGER, 1);
    cache.add(TimerIntent.TRIGGER, 2);

    // then
    assertThat(metrics.get(TimerIntent.TRIGGER)).hasValue(2);
    assertThat(metrics.get(JobIntent.TIME_OUT)).hasValue(1);
  }

  @Test
  void shouldClearCaches() {
    // given
    final var cache =
        BoundedScheduledCommandCache.ofIntent(
            NOOP_METRICS, TimerIntent.TRIGGER, JobIntent.TIME_OUT);
    cache.add(JobIntent.TIME_OUT, 1);
    cache.add(TimerIntent.TRIGGER, 1);

    // when
    cache.clear();

    // then
    assertThat(cache.contains(JobIntent.TIME_OUT, 1)).isFalse();
    assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isFalse();
  }

  @Nested
  final class StagedTest {
    @RegressionTest("https://github.com/camunda/camunda/pull/30560")
    void shouldOnlyStageKeysForConfiguredCachedIntents() {
      // given
      final var cache = BoundedScheduledCommandCache.ofIntent(NOOP_METRICS, TimerIntent.TRIGGER);
      final var staged = cache.stage();

      // when
      staged.add(JobIntent.TIME_OUT, 1);

      // then
      assertThat(staged.contains(JobIntent.TIME_OUT, 1)).isFalse();
    }

    @Test
    void shouldNotContainStagedKeys() {
      // given
      final var cache = BoundedScheduledCommandCache.ofIntent(NOOP_METRICS, TimerIntent.TRIGGER);
      final var staged = cache.stage();

      // when
      staged.add(TimerIntent.TRIGGER, 1);

      // then
      assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isFalse();
    }

    @Test
    void shouldPersistStagedKeys() {
      // given
      final var cache = BoundedScheduledCommandCache.ofIntent(NOOP_METRICS, TimerIntent.TRIGGER);
      final var staged = cache.stage();
      staged.add(TimerIntent.TRIGGER, 1);

      // when
      staged.persist();

      // then
      assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isTrue();
    }

    @Test
    void shouldContainPersistedKeys() {
      // given
      final var cache = BoundedScheduledCommandCache.ofIntent(NOOP_METRICS, TimerIntent.TRIGGER);
      cache.add(TimerIntent.TRIGGER, 1);

      // when
      final var staged = cache.stage();

      // then
      assertThat(staged.contains(TimerIntent.TRIGGER, 1)).isTrue();
    }

    @Test
    void shouldContainStagedKeys() {
      // given
      final var cache = BoundedScheduledCommandCache.ofIntent(NOOP_METRICS, TimerIntent.TRIGGER);
      final var staged = cache.stage();

      // when
      staged.add(TimerIntent.TRIGGER, 1);

      // then
      assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isFalse();
      assertThat(staged.contains(TimerIntent.TRIGGER, 1)).isTrue();
    }

    @Test
    void shouldNotRemoveFromMainCache() {
      // given
      final var cache = BoundedScheduledCommandCache.ofIntent(NOOP_METRICS, TimerIntent.TRIGGER);
      final var staged = cache.stage();
      cache.add(TimerIntent.TRIGGER, 1);

      // when
      staged.remove(TimerIntent.TRIGGER, 1);

      // then
      assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isTrue();
    }

    @Test
    void shouldClearOnlyStagedKeys() {
      // given
      final var cache = BoundedScheduledCommandCache.ofIntent(NOOP_METRICS, TimerIntent.TRIGGER);
      final var staged = cache.stage();
      cache.add(TimerIntent.TRIGGER, 1);
      staged.add(TimerIntent.TRIGGER, 2);

      // when
      staged.clear();

      // then
      assertThat(staged.contains(TimerIntent.TRIGGER, 2)).isFalse();
      assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isTrue();
    }
  }
}
