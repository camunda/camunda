/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_HOST;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.utils.net.Address;
import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beans.GatewayBasedProperties;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  GatewayBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
public class GatewayInternalApiTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.network.internal-api.host=hostNew",
        "camunda.cluster.network.internal-api.port=10",
        "camunda.cluster.network.internal-api.advertised-host=advertisedHostNew",
        "camunda.cluster.network.internal-api.advertised-port=30",
      })
  class WithOnlyUnifiedConfigSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyUnifiedConfigSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetFromNew() {
      assertThat(gatewayCfg.getCluster())
          .returns("hostNew", ClusterCfg::getHost)
          .returns(10, ClusterCfg::getPort)
          .returns("advertisedHostNew", ClusterCfg::getAdvertisedHost)
          .returns(30, ClusterCfg::getAdvertisedPort);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.network.internalApi.host=hostLegacyBroker",
        "zeebe.broker.network.internalApi.port=30",
        "zeebe.broker.network.internalApi.advertisedHost=advertisedHostLegacyBroker",
        "zeebe.broker.network.internalApi.advertisedPort=50"
      })
  class WithOnlyLegacyBrokerSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacyBrokerSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldNotSetFromLegacyBroker() {
      assertThat(gatewayCfg.getCluster())
          .returns(DEFAULT_CLUSTER_HOST, ClusterCfg::getHost)
          .returns(DEFAULT_CLUSTER_PORT, ClusterCfg::getPort)
          .returns(Address.defaultAdvertisedHost().getHostAddress(), ClusterCfg::getAdvertisedHost)
          .returns(DEFAULT_CLUSTER_PORT, ClusterCfg::getAdvertisedPort);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.cluster.host=hostLegacyGateway",
        "zeebe.gateway.cluster.port=20",
        "zeebe.gateway.cluster.advertisedHost=advertisedHostLegacyGateway",
        "zeebe.gateway.cluster.advertisedPort=40"
      })
  class WithOnlyLegacyGatewayPropertiesSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacyGatewayPropertiesSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetFromLegacyGateway() {
      assertThat(gatewayCfg.getCluster())
          .returns("hostLegacyGateway", ClusterCfg::getHost)
          .returns(20, ClusterCfg::getPort)
          .returns("advertisedHostLegacyGateway", ClusterCfg::getAdvertisedHost)
          .returns(40, ClusterCfg::getAdvertisedPort);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.cluster.host=hostLegacyGateway",
        "zeebe.gateway.cluster.port=20",
      })
  class WithOnlyLegacyGatewayHostAndPortSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacyGatewayHostAndPortSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetAdvertisedHostAndGatewayFromLegacyGatewayIfNotSpecified() {
      assertThat(gatewayCfg.getCluster())
          .returns("hostLegacyGateway", ClusterCfg::getAdvertisedHost)
          .returns(20, ClusterCfg::getAdvertisedPort);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.cluster.network.internal-api.host=hostNew",
        "camunda.cluster.network.internal-api.port=10",
        "camunda.cluster.network.internal-api.advertised-host=advertisedHostNew",
        "camunda.cluster.network.internal-api.advertised-port=30",
        // legacy broker
        "zeebe.broker.network.internalApi.host=hostLegacyBroker",
        "zeebe.broker.network.internalApi.port=30",
        "zeebe.broker.network.internalApi.advertisedHost=advertisedHostLegacyBroker",
        "zeebe.broker.network.internalApi.advertisedPort=50",
        // legacy gateway
        "zeebe.gateway.cluster.host=hostLegacyGateway",
        "zeebe.gateway.cluster.port=20",
        "zeebe.gateway.cluster.advertisedHost=advertisedHostLegacyGateway",
        "zeebe.gateway.cluster.advertisedPort=40"
      })
  class WithNewAndLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithNewAndLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetFromNew() {
      assertThat(gatewayCfg.getCluster())
          .returns("hostNew", ClusterCfg::getHost)
          .returns(10, ClusterCfg::getPort)
          .returns("advertisedHostNew", ClusterCfg::getAdvertisedHost)
          .returns(30, ClusterCfg::getAdvertisedPort);
    }
  }
}
