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
import io.camunda.zeebe.broker.system.configuration.engine.MessagesCfg;
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
public class EngineMessagesTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.engine.messages.ttl-checker-batch-limit=50",
        "camunda.processing.engine.messages.ttl-checker-interval=2m",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetMessages() {
      assertThat(brokerCfg.getExperimental().getEngine().getMessages())
          .returns(50, MessagesCfg::getTtlCheckerBatchLimit)
          .returns(Duration.ofMinutes(2), MessagesCfg::getTtlCheckerInterval);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.engine.messages.ttlCheckerBatchLimit=50",
        "zeebe.broker.experimental.engine.messages.ttlCheckerInterval=2m",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetMessagesFromLegacy() {
      assertThat(brokerCfg.getExperimental().getEngine().getMessages())
          .returns(50, MessagesCfg::getTtlCheckerBatchLimit)
          .returns(Duration.ofMinutes(2), MessagesCfg::getTtlCheckerInterval);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.processing.engine.messages.ttl-checker-batch-limit=50",
        "camunda.processing.engine.messages.ttl-checker-interval=2m",
        // legacy
        "zeebe.broker.experimental.engine.messages.ttlCheckerBatchLimit=500",
        "zeebe.broker.experimental.engine.messages.ttlCheckerInterval=20m",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetMessagesFromNew() {
      assertThat(brokerCfg.getExperimental().getEngine().getMessages())
          .returns(50, MessagesCfg::getTtlCheckerBatchLimit)
          .returns(Duration.ofMinutes(2), MessagesCfg::getTtlCheckerInterval);
    }
  }
}
