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
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExporterConfigurationTest {

  @Test
  void shouldHaveDefaultConfiguration() {
    // given

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("default", Map.of());
    final ExportingCfg exportingCfg = cfg.getExporting();

    // then
    assertThat(exportingCfg.skipRecords()).isEqualTo(Set.of());
    assertThat(exportingCfg.distributionInterval()).isEqualTo(Duration.ofSeconds(15));
  }

  @Test
  void shouldSetSkipPositionsForPartitionFromConfigurationFile() {
    // given
    final var environment = new HashMap<String, String>();

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("exporters", environment);
    final ExportingCfg exportingCfg = cfg.getExporting();
    // then
    assertThat(exportingCfg.skipRecords())
        .containsEntry(1, Set.of(999L))
        .containsEntry(2, Set.of(100L));
  }

  @Test
  void shouldSetSkipPositionsForPartitionFromEnvironment() {
    // given
    final var environment = new HashMap<String, String>();
    environment.put("zeebe.broker.exporting.skipRecords.2", "1, 2, 3");
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("exporters", environment);
    final ExportingCfg exportingCfg = cfg.getExporting();

    // then
    assertThat(exportingCfg.skipRecords()).containsEntry(2, Set.of(1L, 2L, 3L));
  }

  @Test
  void shouldSetSkipPositions() {
    // given
    final ExportingCfg exportingCfg = new ExportingCfg(Map.of(1, Set.of(1L, 2L)), null);

    // then
    assertThat(exportingCfg.skipRecords()).containsEntry(1, Set.of(1L, 2L));
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
