/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
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
    assertThat(configuration.getValidatorsResultsOutputMaxSize())
        .isEqualTo(EngineConfiguration.DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE);
    assertThat(configuration.getFormCacheCapacity())
        .isEqualTo(EngineConfiguration.DEFAULT_FORM_CACHE_CAPACITY);
    assertThat(configuration.getProcessCacheCapacity())
        .isEqualTo(EngineConfiguration.DEFAULT_PROCESS_CACHE_CAPACITY);
    assertThat(configuration.getValidatorsResultsOutputMaxSize())
        .isEqualTo(EngineConfiguration.DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE);
    assertThat(configuration.getMaxProcessDepth())
        .isEqualTo(EngineConfiguration.DEFAULT_MAX_PROCESS_DEPTH);
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
    assertThat(configuration.getDrgCacheCapacity()).isEqualTo(2000L);
    assertThat(configuration.getDrgCacheCapacity()).isEqualTo(2000L);
    assertThat(configuration.getValidatorsResultsOutputMaxSize()).isEqualTo(2000);
    assertThat(configuration.getMaxProcessDepth()).isEqualTo(2000);
  }
}
