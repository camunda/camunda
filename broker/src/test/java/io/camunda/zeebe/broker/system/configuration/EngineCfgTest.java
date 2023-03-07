/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class EngineCfgTest {

  public final Map<String, String> environment = new HashMap<>();

  @Test
  void shouldCreateEngineConfigurationFromDefaults() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);

    // when
    final var configuration = cfg.getExperimental().getEngine().createEngineConfiguration();

    // then
    assertThat(configuration.getMessagesTtlCheckerBatchLimit()).isEqualTo(Integer.MAX_VALUE);
    assertThat(configuration.getMessagesTtlCheckerInterval()).isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void shouldCreateEngineConfigurationFromConfig() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("engine", environment);

    // when
    final var configuration = cfg.getExperimental().getEngine().createEngineConfiguration();

    // then
    assertThat(configuration.getMessagesTtlCheckerBatchLimit()).isEqualTo(1000);
    assertThat(configuration.getMessagesTtlCheckerInterval()).isEqualTo(Duration.ofSeconds(15));
  }
}
