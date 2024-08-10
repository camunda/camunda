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

final class PartitionEnableExporterApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);

  private final String exporterId = "exporterA";
  private final int partitionId = 2;
  private final MemberId localMemberId = MemberId.from("3");

  private final PartitionEnableExporterApplier applier =
      new PartitionEnableExporterApplier(
          partitionId, localMemberId, exporterId, Optional.of("other"), partitionChangeExecutor);
  private final ClusterConfiguration clusterConfigWithPartition =
      ClusterConfiguration.init()
          .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
          .updateMember(
              localMemberId,
              m ->
                  m.addPartition(
                      partitionId,
                      PartitionState.active(
                          1, new DynamicPartitionConfig(new ExportersConfig(Map.of())))));

  @Test
  void shouldRejectWhenMemberDoesNotExist() {
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
  void shouldRejectWhenPartitionDoesNotExist() {
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
  void shouldFailIfOtherExporterDoesNotExist() {
    // when
    final var result = applier.initMemberState(clusterConfigWithPartition);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have exporter 'other'");
  }

  @Test
  void shouldFailIfOtherExporterIsDisabled() {
    // given
    final var configWithDisabledExporter =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of("other", new ExporterState(0, State.DISABLED, Optional.empty()))));
    final var clusterConfiguration =
        clusterConfigWithPartition.updateMember(
            localMemberId,
            m -> m.updatePartition(partitionId, p -> p.updateConfig(configWithDisabledExporter)));

    // when
    final var result = applier.initMemberState(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the exporter 'other' is disabled");
  }

  @Test
  void shouldNotChangeStateOnInit() {
    // given
    final var configWithOtherExporter =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of("other", new ExporterState(1, State.ENABLED, Optional.empty()))));
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(
                localMemberId,
                MemberState.initializeAsActive(
                    Map.of(partitionId, PartitionState.active(1, configWithOtherExporter))));

    // when
    final var result = applier.initMemberState(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    final MemberState memberState = clusterConfiguration.getMember(localMemberId);
    assertThat(result.get().apply(memberState)).isEqualTo(memberState);
  }

  @Test
  void shouldFailFutureIfApplyFailed() {
    when(partitionChangeExecutor.enableExporter(partitionId, exporterId, 0, "other"))
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
  void shouldUpdateStateAndMetadataVersionIfApplySucceeded() {
    // given
    when(partitionChangeExecutor.enableExporter(partitionId, exporterId, 1, "other"))
        .thenReturn(CompletableActorFuture.completed(null));

    final var configWithOtherExporter =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of("other", new ExporterState(1, State.ENABLED, Optional.empty()))));
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(
                localMemberId,
                MemberState.initializeAsActive(
                    Map.of(partitionId, PartitionState.active(1, configWithOtherExporter))));
    applier.initMemberState(clusterConfiguration);

    // when
    final var result = applier.applyOperation();

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
    final var updatedState = result.join().apply(clusterConfiguration.getMember(localMemberId));
    final ExporterState updatedExporterState =
        updatedState.getPartition(partitionId).config().exporting().exporters().get(exporterId);
    assertThat(updatedExporterState.state()).isEqualTo(State.ENABLED);
    assertThat(updatedExporterState.metadataVersion()).isEqualTo(1);
    assertThat(updatedExporterState.initializedFrom()).contains("other");
  }
}
