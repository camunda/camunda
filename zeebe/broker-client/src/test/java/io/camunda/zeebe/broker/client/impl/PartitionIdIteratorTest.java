/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static io.camunda.zeebe.broker.client.BrokerMemberIds.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.Protocol;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PartitionIdIteratorTest {
  private final TestTopologyManager topologyManager = new TestTopologyManager();

  @Test
  void shouldIterateOverAllPartitions() {
    // given
    final var iterator =
        new PartitionIdIterator(1, 3, topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME);
    final List<Integer> ids = new ArrayList<>();
    topologyManager.addPartition(1, ZERO).addPartition(2, ZERO).addPartition(3, ZERO);

    // when
    iterator.forEachRemaining(ids::add);

    // then
    assertThat(ids).containsExactly(1, 2, 3);
  }

  @Test
  void shouldSkipPartitionsWithoutLeaders() {
    // given
    final var iterator =
        new PartitionIdIterator(1, 3, topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME);
    final List<Integer> ids = new ArrayList<>();
    topologyManager.addPartition(1, ZERO).addPartition(3, ZERO);

    // when
    iterator.forEachRemaining(ids::add);

    // then
    assertThat(ids).containsExactly(1, 3);
  }

  @Test
  void shouldFilterLeadersByPartitionGroup() {
    // given - a leader for partition 2 only exists in group tenant-b
    topologyManager.addPartition("tenant-b", 2, ZERO);

    // when
    final var defaultIterator =
        new PartitionIdIterator(1, 3, topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME);
    final var tenantIterator = new PartitionIdIterator(1, 3, topologyManager, "tenant-b");
    final List<Integer> ids = new ArrayList<>();
    tenantIterator.forEachRemaining(ids::add);

    // then
    assertThat(defaultIterator.hasNext()).isFalse();
    assertThat(ids).containsExactly(2);
  }

  @Test
  void shouldSkipAllPartitionsWhenNoTopology() {
    // given
    final var topologyManager = new TestTopologyManager(null);

    // when
    final var iterator =
        new PartitionIdIterator(1, 3, topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME);

    // then
    assertThat(iterator.hasNext()).isFalse();
  }
}
