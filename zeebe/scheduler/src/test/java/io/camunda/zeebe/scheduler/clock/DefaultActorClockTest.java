/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

final class DefaultActorClockTest {

  @Test
  void shouldReportCurrentTimeBeforeFirstUpdate() {
    // when
    final var clock = new DefaultActorClock();

    // then
    assertThat(clock.getTimeMillis()).isCloseTo(System.currentTimeMillis(), within(1_000L));
  }
}
