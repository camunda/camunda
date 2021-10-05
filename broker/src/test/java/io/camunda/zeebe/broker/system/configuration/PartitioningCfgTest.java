/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PartitioningCfgTest {
  private final Map<String, String> environment = new HashMap<>();

  /**
   * This test is a smoke test, primarily to ensure backwards compatibility when changing defaults.
   */
  @Test
  void shouldUseRoundRobinAsDefaultScheme() {
    // when
    final var brokerConfig = TestConfigReader.readConfig("empty", environment);
    final var config = brokerConfig.getExperimental().getPartitioning();

    // then
    assertThat(config.getScheme()).isEqualTo(Scheme.ROUND_ROBIN);
  }
}
