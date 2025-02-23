/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

final class BoundedMeterCacheTest {
  private final MeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void shouldRemoveMeterOnEvictionBySize() {
    // given
    final var provider = Counter.builder("test").withRegistry(registry);
    final var cache = new BoundedMeterCache.Builder<>(registry, provider, "foo").maxSize(1).build();

    // when
    cache.get("bar").increment();
    cache.get("baz").increment();

    // then
    assertThat(registry.get("test").tag("foo", "baz").counter().count()).isOne();
    assertThat(registry.find("test").tag("foo", "bar").meter()).isNull();
  }

  @Test
  void shouldRemoveMeterOnEvictionByTTL() {
    // given
    final var provider = Counter.builder("test").withRegistry(registry);
    final var cache =
        new BoundedMeterCache.Builder<>(registry, provider, "foo")
            .maxSize(5)
            .ttl(Duration.ofMillis(10))
            .build();
    final int twentyMs = 20 * 1_000_000;

    // when
    final var start = System.nanoTime();
    cache.get("bar").increment();

    // wait 20ms at least
    do {
      LockSupport.parkNanos(twentyMs);
    } while (System.nanoTime() - start < twentyMs);

    // try accessing a different counter, which should evict the initial one
    cache.get("baz").increment();

    // then
    assertThat(registry.get("test").tag("foo", "baz").counter().count()).isOne();
    assertThat(registry.find("test").tag("foo", "bar").meter()).isNull();
  }

  @Test
  void shouldCreateMeterOnFirstAccess() {
    // given
    final var provider = Counter.builder("test").withRegistry(registry);
    final var cache = new BoundedMeterCache.Builder<>(registry, provider, "foo").build();

    // when
    cache.get("bar").increment();
    cache.get("baz").increment();

    // then
    assertThat(registry.get("test").tag("foo", "baz").counter().count()).isOne();
    assertThat(registry.get("test").tag("foo", "bar").counter().count()).isOne();
  }
}
