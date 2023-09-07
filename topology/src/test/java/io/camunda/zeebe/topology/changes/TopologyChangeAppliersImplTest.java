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
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class TopologyChangeAppliersImplTest {

  private final MemberId localMemberId = MemberId.from("1");

  @Test
  void shouldReturnPartitionJoinApplier() {
    // given
    final var topologyChangeAppliers = new TopologyChangeAppliersImpl(null, localMemberId);
    final var partitionOperation =
        new TopologyChangeOperation.PartitionOperation(
            1, TopologyChangeOperation.PartitionOperationType.JOIN, Optional.of(1));

    // when
    final var applier =
        topologyChangeAppliers.getApplier(
            new TopologyChangeOperation(localMemberId, partitionOperation));

    // then
    assertThat(applier).isInstanceOf(PartitionJoinApplier.class);
  }
}
