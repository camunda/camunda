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

final class StatefulGaugeTest {
  @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void shouldRegisterAsGauge() {
    // given
    final var gauge = StatefulGauge.builder("foo").register(registry);

    // when
    gauge.set(3);

    // then
    final var registered = (StatefulGauge) registry.get("foo").meter();
    assertThat(registered.value()).isEqualTo(3);
  }

  @Test
  void shouldRegisterSameState() {
    // given
    final var first = StatefulGauge.builder("foo").register(registry);
    final var second = StatefulGauge.builder("foo").register(registry);
    first.set(3);

    // when
    second.set(5);

    // then
    final var registered = (StatefulGauge) registry.get("foo").meter();
    assertThat(registered.value()).isEqualTo(5);
  }
}
