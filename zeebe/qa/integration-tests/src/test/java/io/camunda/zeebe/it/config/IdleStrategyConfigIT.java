/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.actor.ActorIdleStrategyConfiguration.IdleStrategySupplier;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class IdleStrategyConfigIT {
  @Test
  void shouldConfigureIdleStrategy() {
    // given
    try (final var gateway = new TestStandaloneGateway()) {
      gateway
          .withProperty("zeebe.actor.idle.maxSpins", 50L)
          .withProperty("zeebe.actor.idle.maxYields", 62L)
          .withProperty("zeebe.actor.idle.minParkPeriod", Duration.ofNanos(100))
          .withProperty("zeebe.actor.idle.maxParkPeriod", Duration.ofNanos(500));

      // when
      gateway.start();

      // then
      final var idleStrategy = gateway.bean(IdleStrategySupplier.class).get();
      assertThat(idleStrategy)
          .hasFieldOrPropertyWithValue("maxSpins", 50L)
          .hasFieldOrPropertyWithValue("maxYields", 62L)
          .hasFieldOrPropertyWithValue("minParkPeriodNs", 100L)
          .hasFieldOrPropertyWithValue("maxParkPeriodNs", 500L);
    }
  }

  @Test
  void shouldConfigureIdleStrategyWithUnifiedConfig() {
    // given
    try (final var gateway = new TestStandaloneGateway()) {
      gateway
          .withProperty("camunda.system.actor.idle.max-spins", 50L)
          .withProperty("camunda.system.actor.idle.max-yields", 62L)
          .withProperty("camunda.system.actor.idle.min-park-period", Duration.ofNanos(100))
          .withProperty("camunda.system.actor.idle.max-park-period", Duration.ofNanos(500));

      // when
      gateway.start();

      // then
      final var idleStrategy = gateway.bean(IdleStrategySupplier.class).get();
      assertThat(idleStrategy)
          .hasFieldOrPropertyWithValue("maxSpins", 50L)
          .hasFieldOrPropertyWithValue("maxYields", 62L)
          .hasFieldOrPropertyWithValue("minParkPeriodNs", 100L)
          .hasFieldOrPropertyWithValue("maxParkPeriodNs", 500L);
    }
  }
}
