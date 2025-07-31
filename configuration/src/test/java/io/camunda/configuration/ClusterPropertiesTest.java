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
public class ClusterPropertiesTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.node-id=7",
        "camunda.cluster.partition-count=5",
        "camunda.cluster.replication-factor=3",
        "camunda.cluster.size=10"
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
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.cluster.nodeId=11",
        "zeebe.broker.cluster.partitionsCount=6",
        "zeebe.broker.cluster.replicationFactor=4",
        "zeebe.broker.cluster.clusterSize=12"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetClusterPropertiesFromLegacy() {
      assertThat(brokerCfg.getCluster().getNodeId()).isEqualTo(11);
      assertThat(brokerCfg.getCluster().getPartitionsCount()).isEqualTo(6);
      assertThat(brokerCfg.getCluster().getReplicationFactor()).isEqualTo(4);
      assertThat(brokerCfg.getCluster().getClusterSize()).isEqualTo(12);
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
        // legacy
        "zeebe.broker.cluster.nodeId=99",
        "zeebe.broker.cluster.partitionsCount=99",
        "zeebe.broker.cluster.replicationFactor=99",
        "zeebe.broker.cluster.clusterSize=99"
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
    }
  }
}
