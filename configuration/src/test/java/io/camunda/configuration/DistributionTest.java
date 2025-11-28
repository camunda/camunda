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
import io.camunda.zeebe.broker.system.configuration.engine.DistributionCfg;
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
public class DistributionTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.engine.distribution.max-backoff-duration=10m",
        "camunda.processing.engine.distribution.redistribution-interval=20s",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDistribution() {
      assertThat(brokerCfg.getExperimental().getEngine().getDistribution())
          .returns(Duration.ofMinutes(10), DistributionCfg::getMaxBackoffDuration)
          .returns(Duration.ofSeconds(20), DistributionCfg::getRedistributionInterval);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.engine.distribution.maxBackoffDuration=10m",
        "zeebe.broker.experimental.engine.distribution.redistributionInterval=20s"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDistributionFromLegacy() {
      assertThat(brokerCfg.getExperimental().getEngine().getDistribution())
          .returns(Duration.ofMinutes(10), DistributionCfg::getMaxBackoffDuration)
          .returns(Duration.ofSeconds(20), DistributionCfg::getRedistributionInterval);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.processing.engine.distribution.max-backoff-duration=10m",
        "camunda.processing.engine.distribution.redistribution-interval=20s",
        // legacy
        "zeebe.broker.experimental.engine.distribution.maxBackoffDuration=99m",
        "zeebe.broker.experimental.engine.distribution.redistributionInterval=99s"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDistributionFromNew() {
      assertThat(brokerCfg.getExperimental().getEngine().getDistribution())
          .returns(Duration.ofMinutes(10), DistributionCfg::getMaxBackoffDuration)
          .returns(Duration.ofSeconds(20), DistributionCfg::getRedistributionInterval);
    }
  }
}
