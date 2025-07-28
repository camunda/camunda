/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.actor.ActorIdleStrategyConfiguration;
import io.camunda.configuration.beans.IdleStrategyBasedProperties;
import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class IdleStrategyConfigTest {
  @Test
  void shouldConfigureIdleStrategy() {
    // given
    final var props =
        new IdleStrategyBasedProperties(50L, 62L, Duration.ofNanos(100), Duration.ofNanos(500));
    final var config = new ActorIdleStrategyConfiguration(props);

    // when
    final var idleStrategy = config.toSupplier().get();

    // then
    assertThat(idleStrategy)
        .hasFieldOrPropertyWithValue("maxSpins", 50L)
        .hasFieldOrPropertyWithValue("maxYields", 62L)
        .hasFieldOrPropertyWithValue("minParkPeriodNs", 100L)
        .hasFieldOrPropertyWithValue("maxParkPeriodNs", 500L);
  }

  @Test
  void shouldUseSchedulerDefaults() {
    // given
    final var props = new IdleStrategyBasedProperties(null, null, null, null);
    final var config = new ActorIdleStrategyConfiguration(props);

    // when
    final var idleStrategy = config.toSupplier().get();

    // then
    assertThat(idleStrategy)
        .hasFieldOrPropertyWithValue("maxSpins", ActorSchedulerBuilder.DEFAULT_MAX_SPINS)
        .hasFieldOrPropertyWithValue("maxYields", ActorSchedulerBuilder.DEFAULT_MAX_YIELDS)
        .hasFieldOrPropertyWithValue(
            "minParkPeriodNs", ActorSchedulerBuilder.DEFAULT_MIN_PARK_PERIOD_NS)
        .hasFieldOrPropertyWithValue(
            "maxParkPeriodNs", ActorSchedulerBuilder.DEFAULT_MAX_PARK_PERIOD_NS);
  }

  @Test
  void shouldAcceptPartialConfig() {
    // given
    final var props = new IdleStrategyBasedProperties(null, 62L, Duration.ofNanos(100), null);
    final var config = new ActorIdleStrategyConfiguration(props);

    // when
    final var idleStrategy = config.toSupplier().get();

    // then
    assertThat(idleStrategy)
        .hasFieldOrPropertyWithValue("maxSpins", ActorSchedulerBuilder.DEFAULT_MAX_SPINS)
        .hasFieldOrPropertyWithValue("maxYields", 62L)
        .hasFieldOrPropertyWithValue("minParkPeriodNs", 100L)
        .hasFieldOrPropertyWithValue(
            "maxParkPeriodNs", ActorSchedulerBuilder.DEFAULT_MAX_PARK_PERIOD_NS);
  }
}
