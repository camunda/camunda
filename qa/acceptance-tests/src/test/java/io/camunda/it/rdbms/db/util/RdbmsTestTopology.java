/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import java.time.Instant;
import java.util.Map;

public final class RdbmsTestTopology {

  private RdbmsTestTopology() {}

  public static BrokerTopologyManager processingTopologyManager() {
    final var partitionGroup =
        PartitionGroupConfiguration.empty(PartitionGroupConfiguration.INITIAL_VERSION)
            .addMember(
                MemberId.from("0"),
                new BrokerPartitionState(1, Instant.EPOCH, Map.of(), Mode.PROCESSING));
    final var clusterConfiguration = mock(CurrentClusterConfiguration.class);
    when(clusterConfiguration.partitionGroup(anyString())).thenReturn(partitionGroup);
    final var topologyManager = mock(BrokerTopologyManager.class);
    when(topologyManager.getClusterConfiguration()).thenReturn(clusterConfiguration);
    return topologyManager;
  }
}
