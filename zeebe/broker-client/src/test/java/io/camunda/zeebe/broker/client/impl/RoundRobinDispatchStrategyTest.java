/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import org.junit.jupiter.api.Test;

final class RoundRobinDispatchStrategyTest {
  private final TestTopologyManager topologyManager = new TestTopologyManager();
  private final RoundRobinDispatchStrategy dispatchStrategy = new RoundRobinDispatchStrategy();

  @Test
  void shouldReturnNullValueIfNoTopology() {
    // given
    final var topologyManager = new TestTopologyManager(null);

    // when
    final var partitionId = dispatchStrategy.determinePartition(topologyManager);

    // then - the null value will be used as fallback by the request manager to redirect to the
    // deployment partition
    assertThat(partitionId).isEqualTo(BrokerClusterState.PARTITION_ID_NULL);
  }

  @Test
  void shouldSkipPartitionsWithoutLeaders() {
    // given
    topologyManager
        .addPartition(1, BrokerClusterState.NODE_ID_NULL)
        .addPartition(2, 0)
        .addPartition(3, 0);

    // when - then
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(2);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(3);
  }
}
