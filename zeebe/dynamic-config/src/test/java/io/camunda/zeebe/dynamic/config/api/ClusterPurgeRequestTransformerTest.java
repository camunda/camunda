/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ClusterPurgeRequestTransformerTest {
  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldPurgeCluster() {
    // given
    final var transformer = new PurgeRequestTransformer();

    final ClusterConfiguration currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(0, PartitionState.active(2, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(0, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)));

    // when
    final var result = transformer.operations(currentTopology);

    // then
    assertThat(result)
        .isRight()
        .right()
        .satisfies(
            operations -> {
              assertThat(operations).hasSize(8);
              assertThat(operations)
                  .containsExactlyInAnyOrder(
                      new PartitionLeaveOperation(id0, 0),
                      new PartitionLeaveOperation(id0, 1),
                      new PartitionLeaveOperation(id1, 0),
                      new PartitionLeaveOperation(id1, 1),
                      new PartitionBootstrapOperation(id0, 0, 2),
                      new PartitionBootstrapOperation(id1, 1, 2),
                      new PartitionJoinOperation(id0, 1, 1),
                      new PartitionJoinOperation(id1, 0, 1));
            });
  }

  @Test
  void shouldPurgeClusterWithThreePartitions() {
    // given
    final var transformer = new PurgeRequestTransformer();

    final ClusterConfiguration currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(0, PartitionState.active(2, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(0, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    // when
    final var result = transformer.operations(currentTopology);

    // then
    assertThat(result)
        .isRight()
        .right()
        .satisfies(
            operations -> {
              assertThat(operations).hasSize(12);
              assertThat(operations)
                  .containsExactlyInAnyOrder(
                      new PartitionLeaveOperation(id0, 0),
                      new PartitionLeaveOperation(id0, 1),
                      new PartitionLeaveOperation(id0, 2),
                      new PartitionLeaveOperation(id1, 0),
                      new PartitionLeaveOperation(id1, 1),
                      new PartitionLeaveOperation(id1, 2),
                      new PartitionBootstrapOperation(id0, 0, 2),
                      new PartitionBootstrapOperation(id0, 2, 2),
                      new PartitionBootstrapOperation(id1, 1, 2),
                      new PartitionJoinOperation(id0, 1, 1),
                      new PartitionJoinOperation(id1, 0, 1),
                      new PartitionJoinOperation(id1, 2, 1));
            });
  }
}
