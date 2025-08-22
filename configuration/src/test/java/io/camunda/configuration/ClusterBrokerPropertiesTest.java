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
public class ClusterBrokerPropertiesTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.node-id=7",
        "camunda.cluster.partition-count=5",
        "camunda.cluster.replication-factor=3",
        "camunda.cluster.size=10",
        "camunda.cluster.compression-algorithm=gzip"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetClusterProperties() {
      assertThat(brokerCfg.getCluster().getNodeId()).isEqualTo(7);
      assertThat(brokerCfg.getCluster().getPartitionsCount()).isEqualTo(5);
      assertThat(brokerCfg.getCluster().getReplicationFactor()).isEqualTo(3);
      assertThat(brokerCfg.getCluster().getClusterSize()).isEqualTo(10);
      assertThat(brokerCfg.getCluster().getMessageCompression())
          .isEqualTo(CompressionAlgorithm.GZIP);
    }
  }

  @Nested
  @TestPropertySource(properties = {"zeebe.gateway.cluster.messageCompression=gzip"})
  class WithOnlyGatewayLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyGatewayLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotSetClusterPropertiesFromLegacyGateway() {
      assertThat(brokerCfg.getCluster().getMessageCompression())
          .isEqualTo(CompressionAlgorithm.NONE);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.cluster.nodeId=11",
        "zeebe.broker.cluster.partitionsCount=6",
        "zeebe.broker.cluster.replicationFactor=4",
        "zeebe.broker.cluster.clusterSize=12",
        "zeebe.broker.cluster.messageCompression=gzip"
      })
  class WithOnlyBrokerLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyBrokerLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetClusterPropertiesFromLegacyBroker() {
      assertThat(brokerCfg.getCluster().getNodeId()).isEqualTo(11);
      assertThat(brokerCfg.getCluster().getPartitionsCount()).isEqualTo(6);
      assertThat(brokerCfg.getCluster().getReplicationFactor()).isEqualTo(4);
      assertThat(brokerCfg.getCluster().getClusterSize()).isEqualTo(12);
      assertThat(brokerCfg.getCluster().getMessageCompression())
          .isEqualTo(CompressionAlgorithm.GZIP);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.cluster.node-id=21",
        "camunda.cluster.partition-count=8",
        "camunda.cluster.replication-factor=5",
        "camunda.cluster.size=15",
        "camunda.cluster.compression-algorithm=gzip",
        // legacy gateway
        "zeebe.gateway.cluster.messageCompression=snappy",
        // legacy broker
        "zeebe.broker.cluster.nodeId=99",
        "zeebe.broker.cluster.partitionsCount=99",
        "zeebe.broker.cluster.replicationFactor=99",
        "zeebe.broker.cluster.clusterSize=99",
        "zeebe.broker.cluster.messageCompression=snappy"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetClusterPropertiesFromNew() {
      assertThat(brokerCfg.getCluster().getNodeId()).isEqualTo(21);
      assertThat(brokerCfg.getCluster().getPartitionsCount()).isEqualTo(8);
      assertThat(brokerCfg.getCluster().getReplicationFactor()).isEqualTo(5);
      assertThat(brokerCfg.getCluster().getClusterSize()).isEqualTo(15);
      assertThat(brokerCfg.getCluster().getMessageCompression())
          .isEqualTo(CompressionAlgorithm.GZIP);
    }
  }
}
