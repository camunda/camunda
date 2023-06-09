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
import org.junit.jupiter.api.Test;

final class FeatureFlagsCfgTest {

  public final Map<String, String> environment = new HashMap<>();

  @Test
  void shouldSetEnableYieldingDueDateCheckerFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableYieldingDueDateChecker()).isTrue();
  }

  @Test
  void shouldSetEnableYieldingDueDateCheckerFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.features.enableYieldingDueDateChecker", "false");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableYieldingDueDateChecker()).isFalse();
  }

  @Test
  void shouldDisableActorMetricsByDefault() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableActorMetrics()).isFalse();
  }

  @Test
  void shouldSetEnableActorMetricsFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableActorMetrics()).isTrue();
  }

  @Test
  void shouldSetEnableActorMetricsFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.features.enableActorMetrics", "false");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableActorMetrics()).isFalse();
  }

  @Test
  void shouldDisableMessageTTLCheckerAsyncByDefault() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableMessageTtlCheckerAsync()).isFalse();
  }

  @Test
  void shouldSetEnableMessageTtlCheckerAsyncFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableMessageTtlCheckerAsync()).isTrue();
  }

  @Test
  void shouldSetEnableMessageTtlCheckerAsyncFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.features.enableMessageTTLCheckerAsync", "true");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableMessageTtlCheckerAsync()).isTrue();
  }

  @Test
  void shouldDisableDueDateCheckerAsyncByDefault() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableTimerDueDateCheckerAsync()).isFalse();
  }

  @Test
  void shouldSetEnableTimerDueDateCheckerAsyncFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableTimerDueDateCheckerAsync()).isTrue();
  }

  @Test
  void shouldSetEnableTimerDueDateCheckerAsyncFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.features.enableMessageDueDateAsync", "true");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableTimerDueDateCheckerAsync()).isTrue();
  }

  @Test
  void shouldSetEnableStraightThroughProcessingLoopDetectorFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableStraightThroughProcessingLoopDetector()).isFalse();
  }

  @Test
  void shouldSetEnableStraightThroughProcessingLoopDetectorFromEnv() {
    // given
    environment.put(
        "zeebe.broker.experimental.features.enableStraightThroughProcessingLoopDetector", "false");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("feature-flags-cfg", environment);
    final var featureFlagsCfg = cfg.getExperimental().getFeatures();

    // then
    assertThat(featureFlagsCfg.isEnableStraightThroughProcessingLoopDetector()).isFalse();
  }
}
