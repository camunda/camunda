/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

final class BoundedMeterCacheTest {
  private final MeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void shouldRemoveMeterOnEvictionBySize() {
    // given
    final var provider = Counter.builder("test").withRegistry(registry);
    final var cache = BoundedMeterCache.of(registry, provider, TestTag.FOO, 1);

    // when
    cache.get("bar").increment();
    cache.get("baz").increment();

    // then
    Awaitility.await("until the eviction listener is invoked asynchronously")
        .untilAsserted(
            () -> {
              assertThat(registry.get("test").tag("foo", "baz").counter().count()).isOne();
              assertThat(registry.find("test").tag("foo", "bar").meter()).isNull();
            });
  }

  @Test
  void shouldCreateMeterOnFirstAccess() {
    // given
    final var provider = Counter.builder("test").withRegistry(registry);
    final var cache = BoundedMeterCache.of(registry, provider, TestTag.FOO, 10);

    // when
    cache.get("bar").increment();
    cache.get("baz").increment();

    // then
    assertThat(registry.get("test").tag("foo", "baz").counter().count()).isOne();
    assertThat(registry.get("test").tag("foo", "bar").counter().count()).isOne();
  }

  @SuppressWarnings("NullableProblems")
  private enum TestTag implements KeyName {
    FOO {
      @Override
      public String asString() {
        return "foo";
      }
    }
  }
}
