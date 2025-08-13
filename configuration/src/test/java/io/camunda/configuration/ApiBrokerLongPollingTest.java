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
public class ApiBrokerLongPollingTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.long-polling.enabled=true",
        "camunda.api.long-polling.timeout=20000",
        "camunda.api.long-polling.probe-timeout=30000",
        "camunda.api.long-polling.min-empty-responses=5"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEnabled() {
      assertThat(brokerCfg.getGateway().getLongPolling().isEnabled()).isTrue();
    }

    @Test
    void shouldSetTimeout() {
      assertThat(brokerCfg.getGateway().getLongPolling().getTimeout()).isEqualTo(20000);
    }

    @Test
    void shouldSetProbeTimeout() {
      assertThat(brokerCfg.getGateway().getLongPolling().getProbeTimeout()).isEqualTo(30000);
    }

    @Test
    void shouldSetMinEmptyResponses() {
      assertThat(brokerCfg.getGateway().getLongPolling().getMinEmptyResponses()).isEqualTo(5);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.longPolling.enabled=false",
        "zeebe.gateway.longPolling.timeout=2",
        "zeebe.gateway.longPolling.probeTimeout=3",
        "zeebe.gateway.longPolling.minEmptyResponses=4"
      })
  class WithOnlyLegacyGatewayLongPollingSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyGatewayLongPollingSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNoSetEnabledFromLegacyGateway() {
      assertThat(brokerCfg.getGateway().getLongPolling().isEnabled())
          .isEqualTo(ConfigurationDefaults.DEFAULT_LONG_POLLING_ENABLED);
    }

    @Test
    void shouldNotSetTimeoutFromLegacyGateway() {
      assertThat(brokerCfg.getGateway().getLongPolling().getTimeout())
          .isEqualTo(ConfigurationDefaults.DEFAULT_LONG_POLLING_TIMEOUT);
    }

    @Test
    void shouldNotSetProbeTimeoutFromLegacyGateway() {
      assertThat(brokerCfg.getGateway().getLongPolling().getProbeTimeout())
          .isEqualTo(ConfigurationDefaults.DEFAULT_PROBE_TIMEOUT);
    }

    @Test
    void shouldNotSetMinEmptyResponsesFromLegacyGateway() {
      assertThat(brokerCfg.getGateway().getLongPolling().getMinEmptyResponses())
          .isEqualTo(ConfigurationDefaults.DEFAULT_LONG_POLLING_EMPTY_RESPONSE_THRESHOLD);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.gateway.longPolling.enabled=true",
        "zeebe.broker.gateway.longPolling.timeout=2",
        "zeebe.broker.gateway.longPolling.probeTimeout=3",
        "zeebe.broker.gateway.longPolling.minEmptyResponses=4"
      })
  class WithOnlyLegacyBrokerLongPollingSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyBrokerLongPollingSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEnabledFromLegacyBroker() {
      assertThat(brokerCfg.getGateway().getLongPolling().isEnabled()).isTrue();
    }

    @Test
    void shouldSetTimeoutFromLegacyBroker() {
      assertThat(brokerCfg.getGateway().getLongPolling().getTimeout()).isEqualTo(2);
    }

    @Test
    void shouldSetProbeTimeoutFromLegacyBroker() {
      assertThat(brokerCfg.getGateway().getLongPolling().getProbeTimeout()).isEqualTo(3);
    }

    @Test
    void shouldSetMinEmptyResponsesFromLegacyBroker() {
      assertThat(brokerCfg.getGateway().getLongPolling().getMinEmptyResponses()).isEqualTo(4);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.api.long-polling.enabled=true",
        "camunda.api.long-polling.timeout=20000",
        "camunda.api.long-polling.probe-timeout=30000",
        "camunda.api.long-polling.min-empty-responses=5",
        // legacy gateway configuration
        "zeebe.gateway.longPolling.enabled=false",
        "zeebe.gateway.longPolling.timeout=2",
        "zeebe.gateway.longPolling.probeTimeout=3",
        "zeebe.gateway.longPolling.minEmptyResponses=4",
        // legacy broker configuration
        "zeebe.broker.gateway.longPolling.enabled=false",
        "zeebe.broker.gateway.longPolling.timeout=2",
        "zeebe.broker.gateway.longPolling.probeTimeout=3",
        "zeebe.broker.gateway.longPolling.minEmptyResponses=4",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEnabledFromNew() {
      assertThat(brokerCfg.getGateway().getLongPolling().isEnabled()).isTrue();
    }

    @Test
    void shouldSetTimeoutFromNew() {
      assertThat(brokerCfg.getGateway().getLongPolling().getTimeout()).isEqualTo(20000);
    }

    @Test
    void shouldSetProbeTimeoutFromNew() {
      assertThat(brokerCfg.getGateway().getLongPolling().getProbeTimeout()).isEqualTo(30000);
    }

    @Test
    void shouldSetMinEmptyResponseFromNew() {
      assertThat(brokerCfg.getGateway().getLongPolling().getMinEmptyResponses()).isEqualTo(5);
    }
  }
}
