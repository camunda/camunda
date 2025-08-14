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
import java.time.Duration;
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
public class DataExportTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.export.distribution-interval=1m",
        "camunda.data.export.skip-records=10,20",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDistributionInterval() {
      assertThat(brokerCfg.getExporting().distributionInterval()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void shouldSetSkipRecords() {
      assertThat(brokerCfg.getExporting().skipRecords()).contains(10L, 20L);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.exporting.distributionInterval=2m",
        "zeebe.broker.exporting.skipRecords=30,40",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDistributionInterval() {
      assertThat(brokerCfg.getExporting().distributionInterval()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void shouldSetSkipRecords() {
      assertThat(brokerCfg.getExporting().skipRecords()).contains(30L, 40L);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.export.distribution-interval=1m",
        "camunda.data.export.skip-records=10,20",
        // legacy
        "zeebe.broker.exporting.distributionInterval=2m",
        "zeebe.broker.exporting.skipRecords=30,40",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDistributionIntervalFromNew() {
      assertThat(brokerCfg.getExporting().distributionInterval()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void shouldSetSkipRecordsFromNew() {
      assertThat(brokerCfg.getExporting().skipRecords()).contains(10L, 20L);
    }
  }
}
