/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExporterConfigurationTest {

  @Test
  void shouldHaveDefaultConfiguration() {
    // given

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("default", Map.of());
    final ExportingCfg exportingCfg = cfg.getExporting();

    // then
    assertThat(exportingCfg.skipRecords()).isEqualTo(Map.of());
    assertThat(exportingCfg.distributionInterval()).isEqualTo(Duration.ofSeconds(15));
  }

  @Test
  void shouldSetDistributionIntervalFromEnvironment() {
    // given
    final var environment = new HashMap<String, String>();
    environment.put("zeebe.broker.exporting.distributionInterval", "1s");
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("exporters", environment);
    final ExportingCfg exportingCfg = cfg.getExporting();

    // then
    assertThat(exportingCfg.distributionInterval()).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void shouldSetDistributionIntervalFromConfigurationFile() {
    // given

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("exporters", Map.of());
    final ExportingCfg exportingCfg = cfg.getExporting();

    // then
    assertThat(exportingCfg.distributionInterval()).isEqualTo(Duration.ofSeconds(5));
  }
}
