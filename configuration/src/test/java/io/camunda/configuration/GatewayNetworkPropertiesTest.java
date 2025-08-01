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

import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beans.GatewayBasedProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  GatewayBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("gateway")
public class GatewayNetworkPropertiesTest {

  @Nested
  @TestPropertySource(properties = {"camunda.cluster.network.host=127.0.0.1"})
  class WithNetworkPropertiesSet {
    final GatewayBasedProperties gatewayCfg;

    WithNetworkPropertiesSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetNetworkProperties() {
      assertThat(gatewayCfg.getCluster().getHost()).isEqualTo("127.0.0.1");
    }
  }

  @Nested
  @TestPropertySource(properties = {"zeebe.broker.network.host=broker.host.com"})
  class WithLegacyBrokerNetworkPropertiesSet {
    final GatewayBasedProperties gatewayCfg;

    WithLegacyBrokerNetworkPropertiesSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldNotSetNetworkPropertiesFromLegacyBroker() {
      assertThat(gatewayCfg.getCluster().getHost()).isEqualTo(DEFAULT_CLUSTER_HOST);
    }
  }

  @Nested
  @TestPropertySource(properties = {"zeebe.gateway.cluster.host=gateway.host.com"})
  class WithLegacyGatewayNetworkPropertiesSet {
    final GatewayBasedProperties gatewayCfg;

    WithLegacyGatewayNetworkPropertiesSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetNetworkPropertiesFromLegacyGateway() {
      assertThat(gatewayCfg.getCluster().getHost()).isEqualTo("gateway.host.com");
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
        "zeebe.gateway.cluster.host=legacy.gateway.com",
      })
  class WithNewAndLegacyNetworkPropertiesSet {
    final GatewayBasedProperties gatewayCfg;

    WithNewAndLegacyNetworkPropertiesSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldPrioritizeNewNetworkProperties() {
      assertThat(gatewayCfg.getCluster().getHost()).isEqualTo("unified.host.com");
    }
  }
}
