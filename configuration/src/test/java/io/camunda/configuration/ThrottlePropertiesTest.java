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
import io.camunda.zeebe.broker.system.configuration.backpressure.ThrottleCfg;
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
public class ThrottlePropertiesTest {

  private static final boolean EXPECTED_THROTTLE_ENABLED = true;
  private static final int EXPECTED_ACCEPTABLE_BACKLOG = 200000;
  private static final int EXPECTED_MINIMUM_LIMIT = 200;

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.flow-control.write.throttle.enabled=" + EXPECTED_THROTTLE_ENABLED,
        "camunda.processing.flow-control.write.throttle.acceptable-backlog="
            + EXPECTED_ACCEPTABLE_BACKLOG,
        "camunda.processing.flow-control.write.throttle.minimum-limit=" + EXPECTED_MINIMUM_LIMIT,
        "camunda.processing.flow-control.write.throttle.resolution=100s"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetWriteProperties() {
      assertThat(brokerBasedProperties.getFlowControl().getWrite().getThrottling())
          .returns(EXPECTED_THROTTLE_ENABLED, ThrottleCfg::isEnabled)
          .returns(EXPECTED_ACCEPTABLE_BACKLOG, ThrottleCfg::getAcceptableBacklog)
          .returns(EXPECTED_MINIMUM_LIMIT, ThrottleCfg::getMinimumLimit)
          .returns(Duration.ofSeconds(100), ThrottleCfg::getResolution);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.flowControl.write.throttling.enabled=" + EXPECTED_THROTTLE_ENABLED,
        "zeebe.broker.flowControl.write.throttling.acceptableBacklog="
            + EXPECTED_ACCEPTABLE_BACKLOG,
        "zeebe.broker.flowControl.write.throttling.minimumLimit=" + EXPECTED_MINIMUM_LIMIT,
        "zeebe.broker.flowControl.write.throttling.resolution=100s"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetProcessingPropertiesFromLegacy() {
      assertThat(brokerBasedProperties.getFlowControl().getWrite().getThrottling())
          .returns(EXPECTED_THROTTLE_ENABLED, ThrottleCfg::isEnabled)
          .returns(EXPECTED_ACCEPTABLE_BACKLOG, ThrottleCfg::getAcceptableBacklog)
          .returns(EXPECTED_MINIMUM_LIMIT, ThrottleCfg::getMinimumLimit)
          .returns(Duration.ofSeconds(100), ThrottleCfg::getResolution);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.processing.flow-control.write.throttle.enabled=" + EXPECTED_THROTTLE_ENABLED,
        "camunda.processing.flow-control.write.throttle.acceptable-backlog="
            + EXPECTED_ACCEPTABLE_BACKLOG,
        "camunda.processing.flow-control.write.throttle.minimum-limit=" + EXPECTED_MINIMUM_LIMIT,
        "camunda.processing.flow-control.write.throttle.resolution=100s",
        // legacy
        "zeebe.broker.flowControl.write.throttling.enabled=false",
        "zeebe.broker.flowControl.write.throttling.acceptableBacklog=300000",
        "zeebe.broker.flowControl.write.throttling.minimumLimit=300",
        "zeebe.broker.flowControl.write.throttling.resolution=200s"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetWriteProperties() {
      assertThat(brokerBasedProperties.getFlowControl().getWrite().getThrottling())
          .returns(EXPECTED_THROTTLE_ENABLED, ThrottleCfg::isEnabled)
          .returns(EXPECTED_ACCEPTABLE_BACKLOG, ThrottleCfg::getAcceptableBacklog)
          .returns(EXPECTED_MINIMUM_LIMIT, ThrottleCfg::getMinimumLimit)
          .returns(Duration.ofSeconds(100), ThrottleCfg::getResolution);
    }
  }
}
