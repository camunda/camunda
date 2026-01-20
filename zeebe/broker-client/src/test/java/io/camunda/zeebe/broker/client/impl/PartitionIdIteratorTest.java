/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PartitionIdIteratorTest {
  private final TestTopologyManager topologyManager = new TestTopologyManager();

  @Test
  void shouldIterateOverAllPartitions() {
    // given
    final var iterator = new PartitionIdIterator("raft-partition", 1, 3, topologyManager);
    final List<Integer> ids = new ArrayList<>();
    topologyManager.addPartition(1, 0).addPartition(2, 0).addPartition(3, 0);

    // when
    iterator.forEachRemaining(ids::add);

    // then
    assertThat(ids).containsExactly(1, 2, 3);
  }

  @Test
  void shouldSkipPartitionsWithoutLeaders() {
    // given
    final var iterator = new PartitionIdIterator("raft-partition", 1, 3, topologyManager);
    final List<Integer> ids = new ArrayList<>();
    topologyManager.addPartition(1, 0).addPartition(3, 0);

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
    final var iterator = new PartitionIdIterator("raft-partition", 1, 3, topologyManager);

    // then
    assertThat(iterator.hasNext()).isFalse();
  }
}
