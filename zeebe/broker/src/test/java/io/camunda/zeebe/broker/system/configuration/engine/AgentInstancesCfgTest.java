/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import org.junit.jupiter.api.Test;

final class AgentInstancesCfgTest {

  @Test
  void shouldAcceptDefaultValues() {
    // given
    final var cfg = new AgentInstancesCfg();

    // when - then
    assertThatCode(() -> cfg.init(new BrokerCfg(), "")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectCompletionBatchLimitBelowOne() {
    // given
    final var cfg = new AgentInstancesCfg();
    cfg.setCompletionBatchLimit(0);

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent instance completion batch limit must be at least 1 but was 0");
  }

  @Test
  void shouldRejectNegativeCompletionBatchLimit() {
    // given
    final var cfg = new AgentInstancesCfg();
    cfg.setCompletionBatchLimit(-100);

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent instance completion batch limit must be at least 1 but was -100");
  }
}
