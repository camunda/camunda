/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class FeatureFlagsCfgTest {

  public final Map<String, String> environment = new HashMap<>();

  @Test
  public void shouldSetEnableYieldingDueDateCheckerFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableYieldingDueDateChecker()).isTrue();
  }

  @Test
  public void shouldSetEnableYieldingDueDateCheckerFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.features.enableYieldingDueDateChecker", "false");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableYieldingDueDateChecker()).isFalse();
  }

  @Test
  public void shouldSetDisableAllocateOptimizationFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isAllocateOptimizationDisabled()).isTrue();
  }

  @Test
  public void shouldDisableAllocateOptimizationFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.features.disableAllocateOptimization", "false");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isAllocateOptimizationDisabled()).isFalse();
  }
}
