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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
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
              assertThat(operations)
                  .containsExactly(
                      new PartitionLeaveOperation(id0, 0, 0),
                      new PartitionLeaveOperation(id0, 1, 0),
                      new PartitionLeaveOperation(id1, 0, 0),
                      new PartitionLeaveOperation(id1, 1, 0),
                      new DeleteHistoryOperation(id0),
                      new UpdateIncarnationNumberOperation(id0),
                      new PartitionBootstrapOperation(
                          id0, 0, 2, Optional.of(partitionConfig), false),
                      new PartitionBootstrapOperation(
                          id1, 1, 2, Optional.of(partitionConfig), false),
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
              assertThat(operations)
                  .containsExactly(
                      new PartitionLeaveOperation(id0, 0, 0),
                      new PartitionLeaveOperation(id0, 1, 0),
                      new PartitionLeaveOperation(id0, 2, 0),
                      new PartitionLeaveOperation(id1, 0, 0),
                      new PartitionLeaveOperation(id1, 1, 0),
                      new PartitionLeaveOperation(id1, 2, 0),
                      new DeleteHistoryOperation(id0),
                      new UpdateIncarnationNumberOperation(id0),
                      new PartitionBootstrapOperation(
                          id0, 0, 2, Optional.of(partitionConfig), false),
                      new PartitionBootstrapOperation(
                          id1, 1, 2, Optional.of(partitionConfig), false),
                      new PartitionBootstrapOperation(
                          id0, 2, 2, Optional.of(partitionConfig), false),
                      new PartitionJoinOperation(id1, 0, 1),
                      new PartitionJoinOperation(id0, 1, 1),
                      new PartitionJoinOperation(id1, 2, 1));
            });
  }

  @Test
  void shouldCreateBootstrapOperationWithExporterConfig() {
    // given
    final var transformer = new PurgeRequestTransformer();
    final ExportingConfig exportingConfig =
        new ExportingConfig(
            ExportingState.EXPORTING,
            Map.of(
                "exporter",
                new ExporterState(1, ExporterState.State.ENABLED, Optional.of("config"))));
    final var partitionConfigWithExporter =
        DynamicPartitionConfig.init().updateExporting(exportingConfig);

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
                  .contains(
                      new PartitionBootstrapOperation(
                          id0, 0, 2, Optional.of(partitionConfigWithExporter), false));
            });
  }

  @Test
  void shouldRecreatePartitionsWithDifferentConfig() {
    // given
    final var transformer = new PurgeRequestTransformer();
    final DynamicPartitionConfig config0 =
        DynamicPartitionConfig.init()
            .updateExporting(
                new ExportingConfig(
                    ExportingState.EXPORTING,
                    Map.of(
                        "exporter",
                        new ExporterState(1, ExporterState.State.ENABLED, Optional.of("config")))));
    final DynamicPartitionConfig config1 =
        DynamicPartitionConfig.init()
            .updateExporting(
                new ExportingConfig(
                    ExportingState.EXPORTING,
                    Map.of(
                        "exporter", new ExporterState(1, State.DISABLED, Optional.of("config")))));

    final ClusterConfiguration currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(0, PartitionState.active(2, config0)))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2, config1)));

    // when
    final var result = transformer.operations(currentTopology);

    // then
    assertThat(result)
        .isRight()
        .right()
        .satisfies(
            operations -> {
              assertThat(operations)
                  .contains(new PartitionBootstrapOperation(id0, 0, 2, Optional.of(config0), false))
                  .contains(
                      new PartitionBootstrapOperation(id1, 1, 2, Optional.of(config1), false));
            });
  }
}
