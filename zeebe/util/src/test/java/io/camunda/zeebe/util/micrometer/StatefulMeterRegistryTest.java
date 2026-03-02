/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

final class StatefulMeterRegistryTest {
  @AutoClose private MeterRegistry wrapped = new SimpleMeterRegistry();
  @AutoClose private final StatefulMeterRegistry registry = new StatefulMeterRegistry(wrapped);

  @Test
  void shouldRemoveStatefulGauge() {
    // given
    final var gauge = StatefulGauge.builder("test").register(registry);
    gauge.increment();

    // when
    registry.remove(gauge);

    // then - re-registering should produce a different stateful gauge instance
    final var duplicate = StatefulGauge.builder("test").register(registry);
    assertThat(duplicate).isNotSameAs(gauge);
  }

  @Test
  void shouldReturnSameStatefulGauge() {
    // given
    final var first = StatefulGauge.builder("test").register(registry);
    first.increment();

    // when
    final var second = StatefulGauge.builder("test").register(registry);

    // then
    assertThat(second).isSameAs(first);
    assertThat(second.value()).isOne();
  }
}
