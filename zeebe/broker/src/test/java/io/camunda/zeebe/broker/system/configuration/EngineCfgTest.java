/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.GlobalListenerConfiguration;
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
    assertThat(configuration.getDrgCacheCapacity()).isEqualTo(1000L);
    assertThat(configuration.getJobsTimeoutCheckerPollingInterval())
        .isEqualTo(Duration.ofSeconds(1));
    assertThat(configuration.getJobsTimeoutCheckerBatchLimit()).isEqualTo(Integer.MAX_VALUE);
    assertThat(configuration.getFormCacheCapacity())
        .isEqualTo(EngineConfiguration.DEFAULT_FORM_CACHE_CAPACITY);
    assertThat(configuration.getProcessCacheCapacity())
        .isEqualTo(EngineConfiguration.DEFAULT_PROCESS_CACHE_CAPACITY);
    assertThat(configuration.getValidatorsResultsOutputMaxSize())
        .isEqualTo(EngineConfiguration.DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE);
    assertThat(configuration.getMaxProcessDepth())
        .isEqualTo(EngineConfiguration.DEFAULT_MAX_PROCESS_DEPTH);
    assertThat(configuration.getCommandRedistributionInterval())
        .isEqualTo(EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL);
    assertThat(configuration.getCommandRedistributionMaxBackoff())
        .isEqualTo(EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF_DURATION);
    assertThat(configuration.getListeners().userTask()).isEmpty();
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
    assertThat(configuration.getDrgCacheCapacity()).isEqualTo(2000L);
    assertThat(configuration.getJobsTimeoutCheckerPollingInterval())
        .isEqualTo(Duration.ofSeconds(15));
    assertThat(configuration.getJobsTimeoutCheckerBatchLimit()).isEqualTo(1000);
    assertThat(configuration.getValidatorsResultsOutputMaxSize()).isEqualTo(2000);
    assertThat(configuration.getMaxProcessDepth()).isEqualTo(2000);
    assertThat(configuration.getCommandRedistributionInterval()).isEqualTo(Duration.ofSeconds(60));
    assertThat(configuration.getCommandRedistributionMaxBackoff())
        .isEqualTo(Duration.ofMinutes(20));
    assertThat(configuration.getListeners().userTask()).hasSize(2);
    final var taskListeners = configuration.getListeners().userTask();
    assertListenerCfg(
        taskListeners.get(0), "test1", new String[] {"creating", "canceling"}, "5", false);
    assertListenerCfg(
        taskListeners.get(1), "test2", new String[] {"assigning", "canceling"}, "2", true);
  }

  void assertListenerCfg(
      final GlobalListenerConfiguration config,
      final String type,
      final String[] eventTypes,
      final String retries,
      final boolean afterNonGlobal) {
    assertThat(config.type()).isEqualTo(type);
    assertThat(config.eventTypes()).containsExactly(eventTypes);
    assertThat(config.retries()).isEqualTo(retries);
    assertThat(config.afterNonGlobal()).isEqualTo(afterNonGlobal);
  }
}
