/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.clustering.topology;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ClusterTopologyManagerTest {

  @Test
  void shouldInitializeClusterTopologyFromBrokerCfg() {
    // given
    final ClusterTopologyManager clusterTopologyManager =
        new ClusterTopologyManager(new TestConcurrencyControl(), new PersistedClusterTopology());
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setClusterSize(3);
    brokerCfg.getCluster().setPartitionsCount(3);
    brokerCfg.getCluster().setReplicationFactor(1);

    // when
    clusterTopologyManager.start(brokerCfg).join();

    // then
    final ClusterTopology clusterTopology = clusterTopologyManager.getClusterTopology().join();
    ClusterTopologyAssert.assertThatClusterTopology(clusterTopology)
        .hasMemberWithPartitions(0, Set.of(1))
        .hasMemberWithPartitions(1, Set.of(2))
        .hasMemberWithPartitions(2, Set.of(3));
  }
}
