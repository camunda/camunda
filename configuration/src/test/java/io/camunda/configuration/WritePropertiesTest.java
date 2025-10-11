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
import io.camunda.zeebe.broker.system.configuration.backpressure.RateLimitCfg;
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
public class WritePropertiesTest {

  private static final boolean EXPECTED_WRITE_ENABLED = true;
  private static final int EXPECTED_LIMIT = 1000;

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.flow-control.write.enabled=" + EXPECTED_WRITE_ENABLED,
        "camunda.processing.flow-control.write.limit=" + EXPECTED_LIMIT,
        "camunda.processing.flow-control.write.ramp-up=10s",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetWriteProperties() {
      assertThat(brokerBasedProperties.getFlowControl().getWrite())
          .returns(EXPECTED_WRITE_ENABLED, RateLimitCfg::isEnabled)
          .returns(EXPECTED_LIMIT, RateLimitCfg::getLimit)
          .returns(Duration.ofSeconds(10), RateLimitCfg::getRampUp);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.flowControl.write.enabled=" + EXPECTED_WRITE_ENABLED,
        "zeebe.broker.flowControl.write.limit=" + EXPECTED_LIMIT,
        "zeebe.broker.flowControl.write.rampUp=10s"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetProcessingPropertiesFromLegacy() {
      assertThat(brokerBasedProperties.getFlowControl().getWrite())
          .returns(EXPECTED_WRITE_ENABLED, RateLimitCfg::isEnabled)
          .returns(EXPECTED_LIMIT, RateLimitCfg::getLimit)
          .returns(Duration.ofSeconds(10), RateLimitCfg::getRampUp);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.processing.flow-control.write.enabled=" + EXPECTED_WRITE_ENABLED,
        "camunda.processing.flow-control.write.limit=" + EXPECTED_LIMIT,
        "camunda.processing.flow-control.write.ramp-up=10s",
        // legacy
        "zeebe.broker.flowControl.write.enabled=false",
        "zeebe.broker.flowControl.write.limit=0",
        "zeebe.broker.flowControl.write.rampUp=99s",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetWriteProperties() {
      assertThat(brokerBasedProperties.getFlowControl().getWrite())
          .returns(EXPECTED_WRITE_ENABLED, RateLimitCfg::isEnabled)
          .returns(EXPECTED_LIMIT, RateLimitCfg::getLimit)
          .returns(Duration.ofSeconds(10), RateLimitCfg::getRampUp);
    }
  }

  @Nested
  class WithoutNewAndLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithoutNewAndLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldNotSetWrite() {
      assertThat(brokerBasedProperties.getFlowControl().getWrite()).isNull();
    }
  }
}
