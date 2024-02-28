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
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExporterConfigurationTest {

  @Test
  void shouldSetSkipPositionsFromEnvironment() {
    // given
    final var environment = new HashMap<String, String>();
    environment.put("zeebe.broker.exporters.elasticsearch.skipRecords", "1, 2, 3 , 3, 2, 1, 0");
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("exporters", environment);
    final ExporterCfg exporterCfg = cfg.getExporters().get("elasticsearch");

    // then
    assertThat(exporterCfg.getSkipRecords()).isEqualTo(Set.of(1L, 2L, 3L, 0L));
  }

  @Test
  void shouldSetSkipPositionsFromConfigurationFile() {
    // given
    final var environment = new HashMap<String, String>();

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("exporters", environment);
    final ExporterCfg exporterCfg = cfg.getExporters().get("elasticsearch");

    // then
    assertThat(exporterCfg.getSkipRecords()).isEqualTo(Set.of(112233L, 445566L));
  }

  @Test
  void shouldSetSkipPositions() {
    // given
    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setSkipRecords(Set.of(1L, 2L));

    // then
    assertThat(exporterCfg.getSkipRecords()).isEqualTo(Set.of(1L, 2L));
  }

  @Test
  void shouldSetSkipPositionsForOtherExporters() {
    // given
    final var environment = new HashMap<String, String>();
    environment.put("zeebe.broker.exporters.openSearch.skipRecords", "1, 2, 3");
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("exporters", environment);
    final ExporterCfg exporterCfg = cfg.getExporters().get("openSearch");

    // then
    assertThat(exporterCfg.getSkipRecords()).isEqualTo(Set.of(1L, 2L, 3L));
  }
}
