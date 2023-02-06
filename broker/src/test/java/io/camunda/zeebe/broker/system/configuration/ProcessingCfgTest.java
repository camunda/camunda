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
  void shouldUseDefaultProcessingBatchLimit() {
    // given
    final var cfg = new ProcessingCfg();

    // when
    final int limit = cfg.getProcessingBatchLimit();

    // then
    assertThat(limit).isEqualTo(100);
  }

  @Test
  void shouldSetProcessingBatchLimit() {
    // given
    final var cfg = new ProcessingCfg();
    cfg.setProcessingBatchLimit(50);

    // when
    final int limit = cfg.getProcessingBatchLimit();

    // then
    assertThat(limit).isEqualTo(50);
  }

  @Test
  void shouldSetProcessingBatchLimitFromConfig() {
    // given
    final var cfg =
        TestConfigReader.readConfig("processing-cfg", Collections.emptyMap()).getProcessing();

    // when
    final int limit = cfg.getProcessingBatchLimit();

    // then
    assertThat(limit).isEqualTo(125);
  }

  @Test
  void shouldSetProcessingBatchLimitFromEnvironment() {
    // given
    final var environment =
        Collections.singletonMap("zeebe.broker.processing.processingBatchLimit", "75");
    final var cfg = TestConfigReader.readConfig("processing-cfg", environment).getProcessing();

    // when
    final var limit = cfg.getProcessingBatchLimit();

    // then
    assertThat(limit).isEqualTo(75);
  }

  @Test
  void shouldRejectInvalidProcessingBatchLimit() {
    // given
    final var environment =
        Collections.singletonMap("zeebe.broker.processing.processingBatchLimit", "-1");

    // then
    assertThatThrownBy(() -> TestConfigReader.readConfig("processing-cfg", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processingBatchLimit must be >= 1");
  }
}
