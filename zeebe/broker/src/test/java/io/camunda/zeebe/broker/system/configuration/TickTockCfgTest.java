/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class TickTockCfgTest {

  @Test
  void shouldNotValidateTickTockWhenDetectorIsDisabled() {
    // given
    final var cfg = new FailureDetectorCfg();
    cfg.getTickTock().setMissedTicksThreshold(0);

    // when - then
    assertThatCode(() -> cfg.init(new BrokerCfg(), "")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectTickIntervalBelowOneMillisecond() {
    // given
    final var cfg = new FailureDetectorCfg();
    cfg.setType(FailureDetectorType.TICK_TOCK);
    cfg.getTickTock().setTickInterval(Duration.ZERO);

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tickInterval");
  }

  @Test
  void shouldRejectMissedTicksThresholdBelowOne() {
    // given
    final var cfg = new FailureDetectorCfg();
    cfg.setType(FailureDetectorType.TICK_TOCK);
    cfg.getTickTock().setMissedTicksThreshold(0);

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missedTicksThreshold");
  }

  @Test
  void shouldDefaultSuspectThresholdToHalfOfMissedTicksThresholdRoundedUp() {
    // given
    final var cfg = new TickTockCfg();
    cfg.setMissedTicksThreshold(5);

    // when - then
    assertThat(cfg.getEffectiveSuspectMissedTicksThreshold()).isEqualTo(3);
  }

  @Test
  void shouldRejectSuspectThresholdAboveMissedTicksThreshold() {
    // given
    final var cfg = new FailureDetectorCfg();
    cfg.setType(FailureDetectorType.TICK_TOCK);
    cfg.getTickTock().setMissedTicksThreshold(3);
    cfg.getTickTock().setSuspectMissedTicksThreshold(4);

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("suspectMissedTicksThreshold");
  }
}
