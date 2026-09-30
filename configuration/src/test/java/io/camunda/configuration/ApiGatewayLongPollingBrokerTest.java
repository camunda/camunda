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
import io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults;
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
public class ApiGatewayLongPollingBrokerTest {
  @Nested
  @TestPropertySource(properties = "camunda.api.long-polling.notification-batch-window=200")
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetNotificationBatchWindow() {
      assertThat(brokerCfg.getGateway().getLongPolling().getNotificationBatchWindow())
          .isEqualTo(Duration.ofMillis(200));
    }
  }

  @Nested
  class WithNothingSet {
    final BrokerBasedProperties brokerCfg;

    WithNothingSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetNotificationBatchWindowToDefault() {
      assertThat(brokerCfg.getGateway().getLongPolling().getNotificationBatchWindow())
          .isEqualTo(ConfigurationDefaults.DEFAULT_NOTIFICATION_BATCH_WINDOW);
    }
  }
}
