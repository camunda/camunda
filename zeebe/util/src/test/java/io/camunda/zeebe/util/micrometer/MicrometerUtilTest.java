/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class MicrometerUtilTest {
  @Test
  void shouldThrowExceptionWhenBucketsExceedLongMax() {
    // given - when
    final var buckets = MicrometerUtil.exponentialBucketDuration(7, 6, 1023, ChronoUnit.MILLIS);

    // then
    assertThat(buckets).hasSizeLessThan(1023).allMatch(d -> d.toMillis() > 0L);
  }

  @Nested
  final class DiscardTest {
    private final MeterRegistry wrapped = new SimpleMeterRegistry();
    private final CompositeMeterRegistry registry = MicrometerUtil.wrap(wrapped, Tags.empty());

    @Test
    void shouldNotCloseWrappedRegistriesOnDiscard() {
      // given - when
      MicrometerUtil.discard(registry);

      // then
      assertThat(wrapped.isClosed()).as("wrapped registry is not closed").isFalse();
    }

    @Test
    void shouldRemoveWrappedRegistryOnDiscard() {
      // given - when
      MicrometerUtil.discard(registry);

      // then
      assertThat(registry.getRegistries()).as("has no more wrapped registries").isEmpty();
    }

    @Test
    void shouldCloseRegistryOnDiscard() {
      // given - when
      MicrometerUtil.discard(registry);

      // then
      assertThat(registry.isClosed()).as("composite registry is closed").isTrue();
    }

    @Test
    void shouldClearRegistryOnDiscard() {
      // given
      registry.counter("foo");

      // when
      MicrometerUtil.discard(registry);

      // then
      assertThat(registry.getMeters()).as("has no meters registered").isEmpty();
    }

    @Test
    void shouldNotRemoveMetersFromWrappedRegistryOnDiscard() {
      // given
      final var counter = wrapped.counter("foo");

      // when
      MicrometerUtil.discard(registry);

      // then
      assertThat(registry.getMeters()).as("has no meters registered").isEmpty();
      assertThat(wrapped.getMeters()).as("has some meters registered").isNotEmpty();
      assertThat(wrapped.get(counter.getId().getName()).counter()).isSameAs(counter);
    }
  }
}
