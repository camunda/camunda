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
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg.NodeCfg;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
public class PartitioningTest {

  private FixedPartitionCfg expectedFixedPartitionCfg1;
  private FixedPartitionCfg expectedFixedPartitionCfg2;

  private FixedPartitionCfg createFixedPartitionCfg(
      final int partitionId, final List<NodeCfg> nodes) {
    final var fixedPartitionCfg = new FixedPartitionCfg();
    fixedPartitionCfg.setPartitionId(partitionId);
    fixedPartitionCfg.setNodes(nodes);
    return fixedPartitionCfg;
  }

  private NodeCfg createNodeCfg(final int nodeId, final int priority) {
    final var nodeCfg = new NodeCfg();
    nodeCfg.setNodeId(nodeId);
    nodeCfg.setPriority(priority);
    return nodeCfg;
  }

  @BeforeEach
  void setUp() {
    expectedFixedPartitionCfg1 =
        createFixedPartitionCfg(2, List.of(createNodeCfg(208, 209), createNodeCfg(218, 219)));
    expectedFixedPartitionCfg2 =
        createFixedPartitionCfg(3, List.of(createNodeCfg(308, 309), createNodeCfg(318, 319)));
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.partitioning.scheme=fixed",
        "camunda.cluster.partitioning.fixed.0.partition-id=2",
        "camunda.cluster.partitioning.fixed.0.nodes.0.node-id=208",
        "camunda.cluster.partitioning.fixed.0.nodes.0.priority=209",
        "camunda.cluster.partitioning.fixed.0.nodes.1.node-id=218",
        "camunda.cluster.partitioning.fixed.0.nodes.1.priority=219",
        "camunda.cluster.partitioning.fixed.1.partition-id=3",
        "camunda.cluster.partitioning.fixed.1.nodes.0.node-id=308",
        "camunda.cluster.partitioning.fixed.1.nodes.0.priority=309",
        "camunda.cluster.partitioning.fixed.1.nodes.1.node-id=318",
        "camunda.cluster.partitioning.fixed.1.nodes.1.priority=319",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetPartitioning() {
      assertThat(brokerCfg.getExperimental().getPartitioning().getScheme().name())
          .isEqualTo(Partitioning.Scheme.FIXED.name());

      assertThat(brokerCfg.getExperimental().getPartitioning().getFixed())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyInAnyOrder(expectedFixedPartitionCfg1, expectedFixedPartitionCfg2);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.partitioning.scheme=fixed",
        "zeebe.broker.experimental.partitioning.fixed.0.partitionId=2",
        "zeebe.broker.experimental.partitioning.fixed.0.nodes.0.nodeId=208",
        "zeebe.broker.experimental.partitioning.fixed.0.nodes.0.priority=209",
        "zeebe.broker.experimental.partitioning.fixed.0.nodes.1.nodeId=218",
        "zeebe.broker.experimental.partitioning.fixed.0.nodes.1.priority=219",
        "zeebe.broker.experimental.partitioning.fixed.1.partitionId=3",
        "zeebe.broker.experimental.partitioning.fixed.1.nodes.0.nodeId=308",
        "zeebe.broker.experimental.partitioning.fixed.1.nodes.0.priority=309",
        "zeebe.broker.experimental.partitioning.fixed.1.nodes.1.nodeId=318",
        "zeebe.broker.experimental.partitioning.fixed.1.nodes.1.priority=319",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetPartitioningFromLegacy() {
      assertThat(brokerCfg.getExperimental().getPartitioning().getScheme().name())
          .isEqualTo(Partitioning.Scheme.FIXED.name());

      assertThat(brokerCfg.getExperimental().getPartitioning().getFixed())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyInAnyOrder(expectedFixedPartitionCfg1, expectedFixedPartitionCfg2);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.cluster.partitioning.scheme=fixed",
        "camunda.cluster.partitioning.fixed.0.partition-id=2",
        "camunda.cluster.partitioning.fixed.0.nodes.0.node-id=208",
        "camunda.cluster.partitioning.fixed.0.nodes.0.priority=209",
        "camunda.cluster.partitioning.fixed.0.nodes.1.node-id=218",
        "camunda.cluster.partitioning.fixed.0.nodes.1.priority=219",
        "camunda.cluster.partitioning.fixed.1.partition-id=3",
        "camunda.cluster.partitioning.fixed.1.nodes.0.node-id=308",
        "camunda.cluster.partitioning.fixed.1.nodes.0.priority=309",
        "camunda.cluster.partitioning.fixed.1.nodes.1.node-id=318",
        "camunda.cluster.partitioning.fixed.1.nodes.1.priority=319",
        // legacy
        "zeebe.broker.experimental.partitioning.scheme=fixed",
        "zeebe.broker.experimental.partitioning.fixed.0.partitionId=8",
        "zeebe.broker.experimental.partitioning.fixed.0.nodes.0.nodeId=808",
        "zeebe.broker.experimental.partitioning.fixed.0.nodes.0.priority=809",
        "zeebe.broker.experimental.partitioning.fixed.0.nodes.1.nodeId=818",
        "zeebe.broker.experimental.partitioning.fixed.0.nodes.1.priority=819",
        "zeebe.broker.experimental.partitioning.fixed.1.partitionId=9",
        "zeebe.broker.experimental.partitioning.fixed.1.nodes.0.nodeId=908",
        "zeebe.broker.experimental.partitioning.fixed.1.nodes.0.priority=909",
        "zeebe.broker.experimental.partitioning.fixed.1.nodes.1.nodeId=918",
        "zeebe.broker.experimental.partitioning.fixed.1.nodes.1.priority=919"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetPartitioningFromNew() {
      assertThat(brokerCfg.getExperimental().getPartitioning().getScheme().name())
          .isEqualTo(Partitioning.Scheme.FIXED.name());

      assertThat(brokerCfg.getExperimental().getPartitioning().getFixed())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyInAnyOrder(expectedFixedPartitionCfg1, expectedFixedPartitionCfg2);
    }
  }
}
