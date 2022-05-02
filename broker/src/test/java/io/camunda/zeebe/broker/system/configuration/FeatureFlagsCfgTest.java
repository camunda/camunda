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

public class FeatureFlagsCfgTest {

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
}
