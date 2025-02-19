/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class MicrometerUtilTest {
  @Test
  public void shouldThrowExceptionWhenBucketsExceedLongMax() {
    final var buckets = MicrometerUtil.exponentialBucketDuration(7, 6, 1023, ChronoUnit.MILLIS);
    assertThat(buckets).hasSizeLessThan(1023).allMatch(d -> d.toMillis() > 0L);
  }

  @Nested
  final class WrapTest {
    private final MeterRegistry wrapped = new SimpleMeterRegistry();

    @Test
    void shouldWrapNothingIfNoParentRegistry() {
      // given / when
      final var registry = MicrometerUtil.wrap(null, Tags.empty());

      // then
      assertThat(registry.getRegistries()).isEmpty();
    }

    @Test
    void shouldAddCommonTagsToWrapped() {
      // given
      final var tags = Tags.of("foo", "bar");

      // when
      final var registry = MicrometerUtil.wrap(wrapped, tags);
      registry.counter("foo").increment();

      // then
      assertThat(registry.get("foo").tag("foo", "bar").counter().count()).isOne();
    }

    @Test
    void shouldForwardMetricsToWrapped() {
      // given
      final var registry = MicrometerUtil.wrap(wrapped, Tags.empty());

      // when
      registry.counter("foo").increment();

      // then
      assertThat(wrapped.get("foo").counter().count()).isOne();
    }
  }

  @Nested
  final class CloseTest {
    private final MeterRegistry wrapped = new SimpleMeterRegistry();
    private final CompositeMeterRegistry registry = MicrometerUtil.wrap(wrapped, Tags.empty());

    @Test
    void shouldNotFailOnNull() {
      // given / when / then
      assertThatCode(() -> MicrometerUtil.close(null)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotCloseWrappedRegistriesOnDiscard() {
      // given - when
      MicrometerUtil.close(registry);

      // then
      assertThat(wrapped.isClosed()).as("wrapped registry is not closed").isFalse();
    }

    @Test
    void shouldRemoveWrappedRegistryOnDiscard() {
      // given - when
      MicrometerUtil.close(registry);

      // then
      assertThat(registry.getRegistries()).as("has no more wrapped registries").isEmpty();
    }

    @Test
    void shouldCloseRegistryOnDiscard() {
      // given - when
      MicrometerUtil.close(registry);

      // then
      assertThat(registry.isClosed()).as("composite registry is closed").isTrue();
    }

    @Test
    void shouldClearRegistryOnDiscard() {
      // given
      registry.counter("foo");

      // when
      MicrometerUtil.close(registry);

      // then
      assertThat(registry.getMeters()).as("has no meters registered").isEmpty();
    }

    @Test
    void shouldNotRemoveMetersFromWrappedRegistryOnDiscard() {
      // given
      final var counter = wrapped.counter("foo");

      // when
      MicrometerUtil.close(registry);

      // then
      assertThat(registry.getMeters()).as("has no meters registered").isEmpty();
      assertThat(wrapped.getMeters()).as("has some meters registered").isNotEmpty();
      assertThat(wrapped.get(counter.getId().getName()).counter()).isSameAs(counter);
    }
  }
}
