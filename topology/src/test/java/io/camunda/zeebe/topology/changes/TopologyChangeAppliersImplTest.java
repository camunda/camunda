/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import org.junit.jupiter.api.Test;

final class TopologyChangeAppliersImplTest {

  private final MemberId localMemberId = MemberId.from("1");

  @Test
  void shouldReturnPartitionJoinApplier() {
    // given
    final var topologyChangeAppliers = new TopologyChangeAppliersImpl(null);
    final var partitionOperation = new PartitionJoinOperation(localMemberId, 1, 1);

    // when
    final var applier = topologyChangeAppliers.getApplier(partitionOperation);

    // then
    assertThat(applier).isInstanceOf(PartitionJoinApplier.class);
  }

  @Test
  void shouldReturnPartitionLeaveApplier() {
    // given
    final var topologyChangeAppliers = new TopologyChangeAppliersImpl(null);
    final var partitionOperation = new PartitionLeaveOperation(localMemberId, 1);

    // when
    final var applier = topologyChangeAppliers.getApplier(partitionOperation);

    // then
    assertThat(applier).isInstanceOf(PartitionLeaveApplier.class);
  }
}
