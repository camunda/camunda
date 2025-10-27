/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class MetricsTest {

  private static final boolean EXPECTED_ACTOR = true;
  private static final boolean EXPECTED_ENABLE_EXPORTER_EXECUTION_METRICS = true;

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.monitoring.metrics.actor=" + EXPECTED_ACTOR,
        "camunda.monitoring.metrics.enable-exporter-execution-metrics="
            + EXPECTED_ENABLE_EXPORTER_EXECUTION_METRICS,
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetMetrics() {
      assertThat(brokerCfg.getExperimental().getFeatures().isEnableActorMetrics())
          .isEqualTo(EXPECTED_ACTOR);
      assertThat(brokerCfg.isExecutionMetricsExporterEnabled())
          .isEqualTo(EXPECTED_ENABLE_EXPORTER_EXECUTION_METRICS);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.features.enableActorMetrics=" + EXPECTED_ACTOR,
        "zeebe.broker.executionMetricsExporterEnabled="
            + EXPECTED_ENABLE_EXPORTER_EXECUTION_METRICS,
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetMetricsFromLegacy() {
      assertThat(brokerCfg.getExperimental().getFeatures().isEnableActorMetrics())
          .isEqualTo(EXPECTED_ACTOR);
      assertThat(brokerCfg.isExecutionMetricsExporterEnabled())
          .isEqualTo(EXPECTED_ENABLE_EXPORTER_EXECUTION_METRICS);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.monitoring.metrics.actor=" + EXPECTED_ACTOR,
        "camunda.monitoring.metrics.enable-exporter-execution-metrics="
            + EXPECTED_ENABLE_EXPORTER_EXECUTION_METRICS,
        // legacy
        "zeebe.broker.experimental.features.enableActorMetrics=false",
        "zeebe.broker.executionMetricsExporterEnabled=false"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetMetrics() {
      assertThat(brokerCfg.getExperimental().getFeatures().isEnableActorMetrics())
          .isEqualTo(EXPECTED_ACTOR);
      assertThat(brokerCfg.isExecutionMetricsExporterEnabled())
          .isEqualTo(EXPECTED_ENABLE_EXPORTER_EXECUTION_METRICS);
    }
  }
}
