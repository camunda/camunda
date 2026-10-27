/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.application.commons.actor.ActorClockConfiguration;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import org.junit.jupiter.api.Test;

public class ActorClockConfigurationTest {

  @Test
  void shouldUseControlledClock() {
    // given
    final var controlled = true;
    final var currentTimeMillis = System.currentTimeMillis();

    // when
    final var actorClockConfiguration = new ActorClockConfiguration(controlled);

    // then
    assertThat(actorClockConfiguration.getClock()).isNotNull();
    assertThat(actorClockConfiguration.getClock().orElseThrow().getClass())
        .isEqualTo(ControlledActorClock.class);
    assertThat(actorClockConfiguration.getClockService().epochMilli())
        .isGreaterThanOrEqualTo(currentTimeMillis);
  }

  @Test
  void shouldUseNoClock() {
    // given
    final var controlled = false;
    final var currentTimeMillis = System.currentTimeMillis();

    // when
    final var actorClockConfiguration = new ActorClockConfiguration(controlled);

    // then
    assertThat(actorClockConfiguration.getClock()).isEmpty();
    assertThat(actorClockConfiguration.getClockService()).isNotNull();
    assertThat(actorClockConfiguration.getClockService().epochMilli())
        .isGreaterThanOrEqualTo(currentTimeMillis);
  }
}
