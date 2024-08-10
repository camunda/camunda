/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class PartitionDisableExporterApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);

  private final String exporterId = "exporterA";
  private final int partitionId = 2;
  private final MemberId localMemberId = MemberId.from("3");

  private final PartitionDisableExporterApplier applier =
      new PartitionDisableExporterApplier(
          partitionId, localMemberId, exporterId, partitionChangeExecutor);

  @Test
  void shouldFailInitIfMemberDoesNotExist() {
    // given
    final var clusterConfiguration = ClusterConfiguration.init();

    // when
    final var result = applier.initMemberState(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("member '3' does not exist in the cluster");
  }

  @Test
  void shouldFailInitIfPartitionDoesNotExist() {
    // given
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = applier.initMemberState(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have the partition '2'");
  }

  @Test
  void shouldFailInitIfExporterDoesNotExist() {
    // given
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(
                localMemberId,
                MemberState.initializeAsActive(
                    Map.of(partitionId, PartitionState.active(1, DynamicPartitionConfig.init()))));

    // when
    final var result = applier.initMemberState(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have the exporter 'exporterA'");
  }

  @Test
  void shouldNotChangeStateInInit() {
    // given
    final var configWithExporter =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of(exporterId, new ExporterState(1, State.ENABLED, Optional.empty()))));
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(
                localMemberId,
                MemberState.initializeAsActive(
                    Map.of(partitionId, PartitionState.active(1, configWithExporter))));

    // when
    final var result = applier.initMemberState(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    final MemberState memberState = clusterConfiguration.getMember(localMemberId);
    assertThat(result.get().apply(memberState)).isEqualTo(memberState);
  }

  @Test
  void shouldFailFutureIfApplyFailed() {
    // given
    when(partitionChangeExecutor.disableExporter(partitionId, exporterId))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("force fail")));

    // when
    final var result = applier.applyOperation();

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("force fail");
  }

  @Test
  void shouldCompleteFutureAndUpdateStateIfApplySucceeds() {
    // given
    final var exporterState = new ExporterState(1, State.ENABLED, Optional.empty());
    final var exporterConfig = new ExportersConfig(Map.of(exporterId, exporterState));
    final var partitionConfig = new DynamicPartitionConfig(exporterConfig);
    final var memberState =
        MemberState.initializeAsActive(
            Map.of(partitionId, PartitionState.active(1, partitionConfig)));

    when(partitionChangeExecutor.disableExporter(partitionId, exporterId))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var result = applier.applyOperation();

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));

    final var updatedState = result.join().apply(memberState);
    assertThat(
            updatedState
                .getPartition(partitionId)
                .config()
                .exporting()
                .exporters()
                .get(exporterId)
                .state())
        .isEqualTo(State.DISABLED);
  }
}
