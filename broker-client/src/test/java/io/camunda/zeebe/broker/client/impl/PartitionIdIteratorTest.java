/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PartitionIdIteratorTest {
  private final BrokerClusterStateImpl topology = new BrokerClusterStateImpl();
  private final TestTopologyManager topologyManager = new TestTopologyManager(topology);

  @Test
  void shouldIterateOverAllPartitions() {
    // given
    final var iterator = new PartitionIdIterator(1, 3, topologyManager);
    final List<Integer> ids = new ArrayList<>();
    topology.addBrokerIfAbsent(0);
    topology.setPartitionLeader(1, 0, 1);
    topology.setPartitionLeader(2, 0, 1);
    topology.setPartitionLeader(3, 0, 1);

    // when
    iterator.forEachRemaining(ids::add);

    // then
    assertThat(ids).containsExactly(1, 2, 3);
  }

  @Test
  void shouldSkipPartitionsWithoutLeaders() {
    // given
    final var iterator = new PartitionIdIterator(1, 3, topologyManager);
    final List<Integer> ids = new ArrayList<>();
    topology.addBrokerIfAbsent(0);
    topology.setPartitionLeader(1, 0, 1);
    topology.setPartitionLeader(3, 0, 1);

    // when
    iterator.forEachRemaining(ids::add);

    // then
    assertThat(ids).containsExactly(1, 3);
  }

  @Test
  void shouldSkipAllPartitionsWhenNoTopology() {
    // given
    final var topologyManager = new TestTopologyManager(null);

    // when
    final var iterator = new PartitionIdIterator(1, 3, topologyManager);

    // then
    assertThat(iterator.hasNext()).isFalse();
  }

  @SuppressWarnings("ClassCanBeRecord")
  private static final class TestTopologyManager implements BrokerTopologyManager {
    private final BrokerClusterState topology;

    private TestTopologyManager(final BrokerClusterState topology) {
      this.topology = topology;
    }

    @Override
    public BrokerClusterState getTopology() {
      return topology;
    }

    @Override
    public void addTopologyListener(final BrokerTopologyListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeTopologyListener(final BrokerTopologyListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void onTopologyUpdated(final ClusterTopology clusterTopology) {
      throw new UnsupportedOperationException();
    }
  }
}
