/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.MessagingConfig.CompressionAlgorithm;
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
public class ClusterGatewayPropertiesTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.compression-algorithm=gzip",
        "camunda.cluster.name=zeebeClusterNew"
      })
  class WithOnlyUnifiedConfigSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyUnifiedConfigSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetClusterProperties() {
      assertThat(gatewayCfg.getCluster())
          .returns(CompressionAlgorithm.GZIP, ClusterCfg::getMessageCompression)
          .returns("zeebeClusterNew", ClusterCfg::getClusterName);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.cluster.messageCompression=gzip",
        "zeebe.broker.cluster.clusterName=zeebeClusterLegacyBroker"
      })
  class WithOnlyBrokerLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyBrokerLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldNotSetClusterPropertiesFromLegacyBroker() {
      assertThat(gatewayCfg.getCluster())
          .returns(CompressionAlgorithm.NONE, ClusterCfg::getMessageCompression)
          .returns("zeebe-cluster", ClusterCfg::getClusterName);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.cluster.messageCompression=gzip",
        "zeebe.gateway.cluster.clusterName=zeebeClusterLegacyGateway"
      })
  class WithOnlyGatewayLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyGatewayLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetClusterPropertiesFromLegacyGateway() {
      assertThat(gatewayCfg.getCluster())
          .returns(CompressionAlgorithm.GZIP, ClusterCfg::getMessageCompression)
          .returns("zeebeClusterLegacyGateway", ClusterCfg::getClusterName);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.cluster.compression-algorithm=gzip",
        "camunda.cluster.name=zeebeClusterNew",
        // legacy broker
        "zeebe.broker.cluster.messageCompression=none",
        "zeebe.broker.cluster.clusterName=zeebeClusterLegacyBroker",
        // legacy gateway
        "zeebe.gateway.cluster.messageCompression=none",
        "zeebe.gateway.cluster.clusterName=zeebeClusterLegacyGateway",
      })
  class WithNewAndLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithNewAndLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetClusterPropertiesFromNew() {
      assertThat(gatewayCfg.getCluster())
          .returns(CompressionAlgorithm.GZIP, ClusterCfg::getMessageCompression)
          .returns("zeebeClusterNew", ClusterCfg::getClusterName);
    }
  }
}
