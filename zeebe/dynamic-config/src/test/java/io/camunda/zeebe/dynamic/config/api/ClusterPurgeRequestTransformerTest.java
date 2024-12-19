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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.util.Map;
import java.util.Optional;
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
              assertThat(operations).hasSize(10);
              assertThat(operations)
                  .containsExactly(
                      new PartitionLeaveOperation(id0, 0),
                      new PartitionLeaveOperation(id0, 1),
                      new PartitionLeaveOperation(id1, 0),
                      new PartitionLeaveOperation(id1, 1),
                      new DeleteHistoryOperation(id0),
                      new DeleteHistoryOperation(id1),
                      new PartitionBootstrapOperation(id0, 0, 2, ExportersConfig.empty()),
                      new PartitionBootstrapOperation(id1, 1, 2, ExportersConfig.empty()),
                      new PartitionJoinOperation(id1, 0, 1),
                      new PartitionJoinOperation(id0, 1, 1));
            });
  }

  @Test
  void purgeClusterShouldBootstrapPartitionsInOrder() {
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
              assertThat(operations).hasSize(14);
              assertThat(operations)
                  .containsExactly(
                      new PartitionLeaveOperation(id0, 0),
                      new PartitionLeaveOperation(id0, 1),
                      new PartitionLeaveOperation(id0, 2),
                      new PartitionLeaveOperation(id1, 0),
                      new PartitionLeaveOperation(id1, 1),
                      new PartitionLeaveOperation(id1, 2),
                      new DeleteHistoryOperation(id0),
                      new DeleteHistoryOperation(id1),
                      new PartitionBootstrapOperation(id0, 0, 2, ExportersConfig.empty()),
                      new PartitionBootstrapOperation(id1, 1, 2, ExportersConfig.empty()),
                      new PartitionBootstrapOperation(id0, 2, 2, ExportersConfig.empty()),
                      new PartitionJoinOperation(id1, 0, 1),
                      new PartitionJoinOperation(id0, 1, 1),
                      new PartitionJoinOperation(id1, 2, 1));
            });
  }

  @Test
  void shouldCreateBootstrapOperationWithExporterConfig() {
    // given
    final var transformer = new PurgeRequestTransformer();
    final ExportersConfig exportersConfig =
        new ExportersConfig(
            Map.of(
                "exporter",
                new ExporterState(1, ExporterState.State.ENABLED, Optional.of("config"))));
    final var partitionConfigWithExporter =
        DynamicPartitionConfig.init().updateExporting(exportersConfig);

    final ClusterConfiguration currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .updateMember(
                id0, m -> m.addPartition(0, PartitionState.active(2, partitionConfigWithExporter)));

    // when
    final var result = transformer.operations(currentTopology);

    // then
    assertThat(result)
        .isRight()
        .right()
        .satisfies(
            operations -> {
              assertThat(operations)
                  .contains(new PartitionBootstrapOperation(id0, 0, 2, exportersConfig));
            });
  }
}
