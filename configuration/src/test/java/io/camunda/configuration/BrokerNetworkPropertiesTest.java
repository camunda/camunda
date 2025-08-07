/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_HOST;
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
public class BrokerNetworkPropertiesTest {

  @Nested
  @TestPropertySource(properties = {"camunda.cluster.network.host=127.0.0.1"})
  class WithNetworkPropertiesSet {
    final BrokerBasedProperties brokerCfg;

    WithNetworkPropertiesSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetNetworkProperties() {
      assertThat(brokerCfg.getNetwork().getHost()).isEqualTo("127.0.0.1");
    }
  }

  @Nested
  @TestPropertySource(properties = {"zeebe.broker.network.host=broker.host.com"})
  class WithLegacyBrokerNetworkPropertiesSet {
    final BrokerBasedProperties brokerCfg;

    WithLegacyBrokerNetworkPropertiesSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetNetworkPropertiesFromLegacyBroker() {
      assertThat(brokerCfg.getNetwork().getHost()).isEqualTo("broker.host.com");
    }
  }

  @Nested
  @TestPropertySource(properties = {"zeebe.gateway.cluster.host=gateway.host.com"})
  class WithLegacyGatewayNetworkPropertiesSet {
    final BrokerBasedProperties brokerCfg;

    WithLegacyGatewayNetworkPropertiesSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotSetNetworkPropertiesFromLegacyGateway() {
      assertThat(brokerCfg.getNetwork().getHost()).isEqualTo(DEFAULT_CLUSTER_HOST);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new unified properties
        "camunda.cluster.network.host=unified.host.com",
        // legacy broker properties
        "zeebe.broker.network.host=legacy.broker.com",
        // legacy gateway properties
        "zeebe.gateway.cluster.host=legacy.gateway.com"
      })
  class WithNewAndLegacyNetworkPropertiesSet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacyNetworkPropertiesSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldPrioritizeNewNetworkProperties() {
      assertThat(brokerCfg.getNetwork().getHost()).isEqualTo("unified.host.com");
    }
  }
}
