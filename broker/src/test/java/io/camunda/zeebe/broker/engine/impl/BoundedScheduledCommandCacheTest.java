/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class BoundedScheduledCommandCacheTest {
  @Test
  void shouldNotCacheUnknownIntents() {
    // given
    final var cache = BoundedScheduledCommandCache.ofIntent(TimerIntent.TRIGGER);

    // when
    cache.add(JobIntent.TIME_OUT, 1);

    // then
    assertThat(cache.contains(JobIntent.TIME_OUT, 1)).isFalse();
  }

  @Test
  void shouldAdd() {
    // given
    final var cache = BoundedScheduledCommandCache.ofIntent(TimerIntent.TRIGGER);

    // when
    cache.add(TimerIntent.TRIGGER, 1);

    // then
    assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isTrue();
  }

  @Test
  void shouldNotContainNonCachedKeyIntentPairWithSameIntent() {
    // given
    final var cache = BoundedScheduledCommandCache.ofIntent(TimerIntent.TRIGGER);

    // when
    cache.add(TimerIntent.TRIGGER, 1);

    // then
    assertThat(cache.contains(TimerIntent.TRIGGER, 2)).isFalse();
  }

  @Test
  void shouldNotContainNonCachedKeyIntentPairWithSameKey() {
    // given
    final var cache =
        BoundedScheduledCommandCache.ofIntent(TimerIntent.TRIGGER, JobIntent.TIME_OUT);

    // when
    cache.add(JobIntent.TIME_OUT, 1);

    // then
    assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isFalse();
  }

  @Test
  void shouldRemoveCachedIntentKeyPair() {
    // given
    final var cache =
        BoundedScheduledCommandCache.ofIntent(TimerIntent.TRIGGER, JobIntent.TIME_OUT);
    cache.add(JobIntent.TIME_OUT, 1);
    cache.add(TimerIntent.TRIGGER, 1);

    // when
    cache.remove(JobIntent.TIME_OUT, 1);

    // then
    assertThat(cache.contains(JobIntent.TIME_OUT, 1)).isFalse();
    assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isTrue();
  }

  @Nested
  final class StagedTest {
    @Test
    void shouldNotContainStagedKeys() {
      // given
      final var cache = BoundedScheduledCommandCache.ofIntent(TimerIntent.TRIGGER);
      final var staged = cache.stage();

      // when
      staged.add(TimerIntent.TRIGGER, 1);

      // then
      assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isFalse();
    }

    @Test
    void shouldPersistStagedKeys() {
      // given
      final var cache = BoundedScheduledCommandCache.ofIntent(TimerIntent.TRIGGER);
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
      final var cache = BoundedScheduledCommandCache.ofIntent(TimerIntent.TRIGGER);
      cache.add(TimerIntent.TRIGGER, 1);

      // when
      final var staged = cache.stage();

      // then
      assertThat(staged.contains(TimerIntent.TRIGGER, 1)).isTrue();
    }

    @Test
    void shouldContainStagedKeys() {
      // given
      final var cache = BoundedScheduledCommandCache.ofIntent(TimerIntent.TRIGGER);
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
      final var cache = BoundedScheduledCommandCache.ofIntent(TimerIntent.TRIGGER);
      final var staged = cache.stage();
      cache.add(TimerIntent.TRIGGER, 1);

      // when
      staged.remove(TimerIntent.TRIGGER, 1);

      // then
      assertThat(cache.contains(TimerIntent.TRIGGER, 1)).isTrue();
    }
  }
}
