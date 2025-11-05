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
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import java.util.Collections;
import java.util.List;
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
  @Test
  public void shouldMatchThePropertyNamesInClusterCfg() {
    /*
     * The same property names are defined twice: broker does not depend on configuration, but we
     * would like to log them to provide a better user feedback. Considering they should not change,
     * this simple test is enough to prevent any mistake.
     */
    assertThat(Cluster.LEGACY_INITIAL_CONTACT_POINTS_PROPERTY)
        .isEqualTo(SystemContext.LEGACY_INITIAL_CONTACT_POINTS_PROPERTY);
    assertThat(Cluster.UNIFIED_INITIAL_CONTACT_POINTS_PROPERTY)
        .isEqualTo(SystemContext.UNIFIED_INITIAL_CONTACT_POINTS_PROPERTY);
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.node-id=7",
        "camunda.cluster.partition-count=5",
        "camunda.cluster.replication-factor=3",
        "camunda.cluster.size=10",
        "camunda.cluster.compression-algorithm=gzip",
        "camunda.cluster.name=zeebeClusterNew",
        "camunda.cluster.initial-contact-points=1new,2new"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetClusterProperties() {
      assertThat(brokerCfg.getCluster())
          .returns(7, ClusterCfg::getNodeId)
          .returns(5, ClusterCfg::getPartitionsCount)
          .returns(3, ClusterCfg::getReplicationFactor)
          .returns(10, ClusterCfg::getClusterSize)
          .returns(CompressionAlgorithm.GZIP, ClusterCfg::getMessageCompression)
          .returns("zeebeClusterNew", ClusterCfg::getClusterName)
          .returns(List.of("1new", "2new"), ClusterCfg::getInitialContactPoints);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.cluster.messageCompression=gzip",
        "zeebe.gateway.cluster.clusterName=zeebeClusterLegacyGateway",
        "zeebe.gateway.cluster.initialContactPoints=1LegacyGateway"
      })
  class WithOnlyGatewayLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyGatewayLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotSetClusterPropertiesFromLegacyGateway() {
      assertThat(brokerCfg.getCluster())
          .returns(CompressionAlgorithm.NONE, ClusterCfg::getMessageCompression)
          .returns("zeebe-cluster", ClusterCfg::getClusterName)
          .returns(Collections.emptyList(), ClusterCfg::getInitialContactPoints);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.cluster.nodeId=11",
        "zeebe.broker.cluster.partitionsCount=6",
        "zeebe.broker.cluster.replicationFactor=4",
        "zeebe.broker.cluster.clusterSize=12",
        "zeebe.broker.cluster.messageCompression=gzip",
        "zeebe.broker.cluster.clusterName=zeebeClusterLegacyBroker",
        "zeebe.broker.cluster.initialContactPoints=1LegacyBroker,2LegacyBroker"
      })
  class WithOnlyBrokerLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyBrokerLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetClusterPropertiesFromLegacyBroker() {
      assertThat(brokerCfg.getCluster())
          .returns(11, ClusterCfg::getNodeId)
          .returns(6, ClusterCfg::getPartitionsCount)
          .returns(4, ClusterCfg::getReplicationFactor)
          .returns(12, ClusterCfg::getClusterSize)
          .returns(CompressionAlgorithm.GZIP, ClusterCfg::getMessageCompression)
          .returns("zeebeClusterLegacyBroker", ClusterCfg::getClusterName)
          .returns(List.of("1LegacyBroker", "2LegacyBroker"), ClusterCfg::getInitialContactPoints);
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
        "camunda.cluster.name=zeebeClusterNew",
        "camunda.cluster.initial-contact-points=1new,2new",
        // legacy gateway
        "zeebe.gateway.cluster.messageCompression=snappy",
        "zeebe.gateway.cluster.clusterName=zeebeClusterLegacyGateway",
        "zeebe.gateway.cluster.initialContactPoints=1LegacyGateway",
        // legacy broker
        "zeebe.broker.cluster.nodeId=99",
        "zeebe.broker.cluster.partitionsCount=99",
        "zeebe.broker.cluster.replicationFactor=99",
        "zeebe.broker.cluster.clusterSize=99",
        "zeebe.broker.cluster.messageCompression=snappy",
        "zeebe.broker.cluster.clusterName=zeebeClusterLegacyBroker",
        "zeebe.broker.cluster.initialContactPoints=1LegacyBroker,2LegacyBroker"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetClusterPropertiesFromNew() {
      assertThat(brokerCfg.getCluster())
          .returns(21, ClusterCfg::getNodeId)
          .returns(8, ClusterCfg::getPartitionsCount)
          .returns(5, ClusterCfg::getReplicationFactor)
          .returns(15, ClusterCfg::getClusterSize)
          .returns(CompressionAlgorithm.GZIP, ClusterCfg::getMessageCompression)
          .returns("zeebeClusterNew", ClusterCfg::getClusterName)
          .returns(List.of("1new", "2new"), ClusterCfg::getInitialContactPoints);
    }
  }
}
