/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import org.junit.jupiter.api.Test;

final class ProcessingCfgTest {

  @Test
  void shouldUseDefaultMaxCommandsInBatch() {
    // given
    final var cfg = new ProcessingCfg();

    // when
    final int limit = cfg.getMaxCommandsInBatch();

    // then
    assertThat(limit).isEqualTo(100);
  }

  @Test
  void shouldSetMaxCommandsInBatch() {
    // given
    final var cfg = new ProcessingCfg();
    cfg.setMaxCommandsInBatch(50);

    // when
    final int limit = cfg.getMaxCommandsInBatch();

    // then
    assertThat(limit).isEqualTo(50);
  }

  @Test
  void shouldSetMaxCommandsInBatchFromConfig() {
    // given
    final var cfg =
        TestConfigReader.readConfig("processing-cfg", Collections.emptyMap()).getProcessing();

    // when
    final int limit = cfg.getMaxCommandsInBatch();

    // then
    assertThat(limit).isEqualTo(125);
  }

  @Test
  void shouldSetMaxCommandsInBatchFromEnvironment() {
    // given
    final var environment =
        Collections.singletonMap("zeebe.broker.processing.maxCommandsInBatch", "75");
    final var cfg = TestConfigReader.readConfig("processing-cfg", environment).getProcessing();

    // when
    final var limit = cfg.getMaxCommandsInBatch();

    // then
    assertThat(limit).isEqualTo(75);
  }

  @Test
  void shouldRejectInvalidMaxCommandsInBatch() {
    // given
    final var environment =
        Collections.singletonMap("zeebe.broker.processing.maxCommandsInBatch", "-1");

    // then
    assertThatThrownBy(() -> TestConfigReader.readConfig("processing-cfg", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxCommandsInBatch must be >= 1");
  }
}
