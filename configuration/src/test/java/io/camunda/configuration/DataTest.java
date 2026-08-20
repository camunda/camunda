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
public class DataTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.snapshot-period=10m",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetSnapshotPeriod() {
      assertThat(brokerCfg.getData().getSnapshotPeriod()).isEqualTo(Duration.ofMinutes(10));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.data.snapshotPeriod=15m",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDistributionInterval() {
      assertThat(brokerCfg.getData().getSnapshotPeriod()).isEqualTo(Duration.ofMinutes(15));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.snapshot-period=10m",
        // legacy
        "zeebe.broker.data.snapshotPeriod=15m",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetSnapshotPeriodFromNew() {
      assertThat(brokerCfg.getData().getSnapshotPeriod()).isEqualTo(Duration.ofMinutes(10));
    }
  }
}
