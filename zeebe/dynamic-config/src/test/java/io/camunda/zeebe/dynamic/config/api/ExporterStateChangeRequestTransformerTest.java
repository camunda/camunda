/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExporterStateChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ExporterStateChangeRequestTransformerTest {

  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");

  private MemberState memberWith(final ExportingState state, final int... partitionIds) {
    final var config = new DynamicPartitionConfig(new ExportingConfig(state, Map.of()));
    final var partitions = new java.util.HashMap<Integer, PartitionState>();
    for (final int partitionId : partitionIds) {
      partitions.put(partitionId, PartitionState.active(1, config));
    }
    return MemberState.initializeAsActive(partitions);
  }

  @Test
  void shouldGenerateOneOperationPerPartitionOwningMemberNotInTargetState() {
    // given — both members export, request pauses
    final var transformer = new ExporterStateChangeRequestTransformer(ExportingState.PAUSED);
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(id0, memberWith(ExportingState.EXPORTING, 1))
            .addMember(id1, memberWith(ExportingState.EXPORTING, 2));

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactlyInAnyOrder(
            new ExporterStateChangeOperation(id0, ExportingState.PAUSED),
            new ExporterStateChangeOperation(id1, ExportingState.PAUSED));
  }

  @Test
  void shouldSkipMemberAlreadyFullyInTargetState() {
    // given — id0 exporting, id1 already paused
    final var transformer = new ExporterStateChangeRequestTransformer(ExportingState.PAUSED);
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(id0, memberWith(ExportingState.EXPORTING, 1))
            .addMember(id1, memberWith(ExportingState.PAUSED, 2));

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then — only the member not yet in the target state gets an operation
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(new ExporterStateChangeOperation(id0, ExportingState.PAUSED));
  }

  @Test
  void shouldTargetMemberWithAnyPartitionNotInTargetState() {
    // given — id0 has one exporting and one paused partition, request pauses
    final var config =
        new DynamicPartitionConfig(new ExportingConfig(ExportingState.PAUSED, Map.of()));
    final var exportingConfig =
        new DynamicPartitionConfig(new ExportingConfig(ExportingState.EXPORTING, Map.of()));
    final var member =
        MemberState.initializeAsActive(
            Map.of(
                1, PartitionState.active(1, config),
                2, PartitionState.active(1, exportingConfig)));
    final var transformer = new ExporterStateChangeRequestTransformer(ExportingState.PAUSED);
    final var clusterConfiguration = ClusterConfiguration.init().addMember(id0, member);

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(new ExporterStateChangeOperation(id0, ExportingState.PAUSED));
  }

  @Test
  void shouldSkipMembersWithoutPartitions() {
    // given — id0 owns a partition, id1 owns none
    final var transformer = new ExporterStateChangeRequestTransformer(ExportingState.PAUSED);
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(id0, memberWith(ExportingState.EXPORTING, 1))
            .addMember(id1, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(new ExporterStateChangeOperation(id0, ExportingState.PAUSED));
  }

  @Test
  void shouldReturnEmptyPlanWhenAllMembersAlreadyInTargetState() {
    // given — everything already paused, request pauses again
    final var transformer = new ExporterStateChangeRequestTransformer(ExportingState.PAUSED);
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(id0, memberWith(ExportingState.PAUSED, 1))
            .addMember(id1, memberWith(ExportingState.PAUSED, 2));

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then — idempotent no-op yields an empty plan
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isEmpty();
  }
}
