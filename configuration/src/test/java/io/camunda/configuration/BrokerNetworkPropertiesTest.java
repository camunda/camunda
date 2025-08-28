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
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.unit.DataSize;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class BrokerNetworkPropertiesTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.network.host=127.0.0.1",
        "camunda.cluster.network.advertised-host=advertised.host.com",
        "camunda.cluster.network.port-offset=100",
        "camunda.cluster.network.max-message-size=8MB",
        "camunda.cluster.network.socket-send-buffer=2MB",
        "camunda.cluster.network.socket-receive-buffer=3MB",
        "camunda.cluster.network.heartbeat-timeout=30s",
        "camunda.cluster.network.heartbeat-interval=10s",
        "camunda.cluster.network.min-keep-alive-interval=45s"
      })
  class WithNetworkPropertiesSet {
    final BrokerBasedProperties brokerCfg;

    WithNetworkPropertiesSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetAllNetworkProperties() {
      assertThat(brokerCfg.getNetwork().getHost()).isEqualTo("127.0.0.1");
      assertThat(brokerCfg.getNetwork().getAdvertisedHost()).isEqualTo("advertised.host.com");
      assertThat(brokerCfg.getNetwork().getPortOffset()).isEqualTo(100);
      assertThat(brokerCfg.getNetwork().getMaxMessageSize()).isEqualTo(DataSize.ofMegabytes(8));
      assertThat(brokerCfg.getNetwork().getSocketSendBuffer()).isEqualTo(DataSize.ofMegabytes(2));
      assertThat(brokerCfg.getNetwork().getSocketReceiveBuffer())
          .isEqualTo(DataSize.ofMegabytes(3));
      assertThat(brokerCfg.getNetwork().getHeartbeatTimeout()).isEqualTo(Duration.ofSeconds(30));
      assertThat(brokerCfg.getNetwork().getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(10));
      assertThat(brokerCfg.getGateway().getNetwork().getMaxMessageSize())
          .isEqualTo(DataSize.ofMegabytes(8));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.network.host=broker.host.com",
        "zeebe.broker.network.advertisedHost=legacy.advertised.com",
        "zeebe.broker.network.portOffset=200",
        "zeebe.broker.network.maxMessageSize=13MB",
        "zeebe.broker.network.socketSendBuffer=4MB",
        "zeebe.broker.network.socketReceiveBuffer=5MB",
        "zeebe.broker.network.heartbeatTimeout=45s",
        "zeebe.broker.network.heartbeatInterval=15s",
        "zeebe.broker.gateway.network.maxMessageSize=13MB"
      })
  class WithLegacyBrokerNetworkPropertiesSet {
    final BrokerBasedProperties brokerCfg;

    WithLegacyBrokerNetworkPropertiesSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetNetworkPropertiesFromLegacyBroker() {
      assertThat(brokerCfg.getNetwork().getHost()).isEqualTo("broker.host.com");
      assertThat(brokerCfg.getNetwork().getAdvertisedHost()).isEqualTo("legacy.advertised.com");
      assertThat(brokerCfg.getNetwork().getPortOffset()).isEqualTo(200);
      assertThat(brokerCfg.getNetwork().getMaxMessageSize()).isEqualTo(DataSize.ofMegabytes(13));
      assertThat(brokerCfg.getNetwork().getSocketSendBuffer()).isEqualTo(DataSize.ofMegabytes(4));
      assertThat(brokerCfg.getNetwork().getSocketReceiveBuffer())
          .isEqualTo(DataSize.ofMegabytes(5));
      assertThat(brokerCfg.getNetwork().getHeartbeatTimeout()).isEqualTo(Duration.ofSeconds(45));
      assertThat(brokerCfg.getNetwork().getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
      assertThat(brokerCfg.getGateway().getNetwork().getMaxMessageSize())
          .isEqualTo(DataSize.ofMegabytes(13));
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
        "camunda.cluster.network.advertised-host=unified.advertised.com",
        "camunda.cluster.network.port-offset=300",
        "camunda.cluster.network.max-message-size=11MB",
        // legacy broker properties
        "zeebe.broker.network.host=legacy.broker.com",
        "zeebe.broker.network.advertisedHost=legacy.broker.advertised.com",
        "zeebe.broker.network.portOffset=400",
        "zeebe.broker.network.maxMessageSize=16MB",
        "zeebe.broker.gateway.network.maxMessageSize=16MB",
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
      assertThat(brokerCfg.getNetwork().getAdvertisedHost()).isEqualTo("unified.advertised.com");
      assertThat(brokerCfg.getNetwork().getPortOffset()).isEqualTo(300);
      assertThat(brokerCfg.getGateway().getNetwork().getMaxMessageSize())
          .isEqualTo(DataSize.ofMegabytes(11));
      assertThat(brokerCfg.getNetwork().getMaxMessageSize()).isEqualTo(DataSize.ofMegabytes(11));
    }
  }
}
