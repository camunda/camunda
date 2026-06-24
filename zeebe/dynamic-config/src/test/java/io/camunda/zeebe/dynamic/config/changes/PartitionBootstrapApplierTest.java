/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.PartitionStateAssert;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class PartitionBootstrapApplierTest {

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final int partitionId = 2;
  private final int priority = 1;
  private final MemberId localMemberId = MemberId.from("1");
  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);

  @Nested
  final class Init {

    final ClusterConfiguration initialConfiguration =
        ClusterConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .updateMember(
                localMemberId,
                m -> m.addPartition(1, PartitionState.active(priority, partitionConfig)));

    @Test
    void shouldRejectIfPartitionAlreadyExistsInOtherMembers() {
      // given
      final var otherMemberId = MemberId.from("2");
      final var configurationWithActivePartition =
          initialConfiguration
              .addMember(otherMemberId, MemberState.initializeAsActive(Map.of()))
              .updateMember(
                  otherMemberId,
                  m ->
                      m.addPartition(
                          partitionId, PartitionState.active(priority, partitionConfig)));

      // when
      final var result =
          new PartitionBootstrapApplier(
                  partitionId, priority, localMemberId, false, partitionChangeExecutor)
              .init(configurationWithActivePartition);

      // then
      EitherAssert.assertThat(result).isLeft().left().isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectIfPartitionIdIsNotContiguous() {
      // when
      final var result =
          new PartitionBootstrapApplier(
                  partitionId + 1, priority, localMemberId, false, partitionChangeExecutor)
              .init(initialConfiguration);

      // then
      EitherAssert.assertThat(result).isLeft().left().isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectIfLocalMemberIsNotActive() {
      // given
      final var configurationWithInactiveMember = ClusterConfiguration.init();

      // when
      final var result =
          new PartitionBootstrapApplier(
                  partitionId, priority, localMemberId, false, partitionChangeExecutor)
              .init(configurationWithInactiveMember);

      // then
      EitherAssert.assertThat(result).isLeft().left().isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotRejectBootstrapIfPartitionIsAlreadyBootstrappingInLocalMember() {
      // given
      final var topologyWithPartitionBootstrapping =
          initialConfiguration.updateMember(
              localMemberId,
              m ->
                  m.addPartition(
                      partitionId, PartitionState.bootstrapping(partitionId, partitionConfig)));
      final var applier =
          new PartitionBootstrapApplier(
              partitionId, priority, localMemberId, true, partitionChangeExecutor);
      when(partitionChangeExecutor.bootstrap(anyInt(), anyInt(), any(), anyBoolean()))
          .thenReturn(CompletableActorFuture.completed());

      // when
      final var result = applier.init(topologyWithPartitionBootstrapping);

      // then
      EitherAssert.assertThat(result).isRight();
      assertThat(result.get().apply(topologyWithPartitionBootstrapping))
          .describedAs("No change to the topology")
          .isEqualTo(topologyWithPartitionBootstrapping);

      assertThat(applier.applyOperation()).succeedsWithin(Duration.ofSeconds(1));
      final var configCaptor = ArgumentCaptor.forClass(DynamicPartitionConfig.class);
      verify(partitionChangeExecutor).bootstrap(eq(2), anyInt(), configCaptor.capture(), eq(true));
      assertThat(configCaptor.getValue()).isEqualTo(partitionConfig);
    }

    @Test
    void shouldUpdatePartitionStateToBootstrapping() {
      // when
      final var result =
          new PartitionBootstrapApplier(
                  partitionId, priority, localMemberId, false, partitionChangeExecutor)
              .init(initialConfiguration);

      // then
      EitherAssert.assertThat(result).isRight();
      ClusterConfigurationAssert.assertThatClusterTopology(result.get().apply(initialConfiguration))
          .describedAs("Partition added at state bootstrapping")
          .member(localMemberId)
          .hasPartitionSatisfying(
              partitionId,
              state ->
                  PartitionStateAssert.assertThat(state)
                      .hasConfig(partitionConfig)
                      .hasState(State.BOOTSTRAPPING)
                      .hasPriority(priority));
    }

    @Test
    void shouldBootstrapWithGivenPartitionConfig() {
      // given
      final ClusterConfiguration configWithoutPartition =
          ClusterConfiguration.init()
              .addMember(localMemberId, MemberState.initializeAsActive(Map.of()));
      final DynamicPartitionConfig partitionConfig =
          DynamicPartitionConfig.init()
              .updateExporting(
                  new ExportingConfig(
                      ExportingState.EXPORTING,
                      Map.of(
                          "exporter",
                          new ExporterState(
                              1, ExporterState.State.ENABLED, Optional.of("config")))));

      // when
      final var result =
          new PartitionBootstrapApplier(
                  new PartitionBootstrapOperation(
                      localMemberId, 1, priority, Optional.of(partitionConfig), false),
                  partitionChangeExecutor)
              .init(configWithoutPartition);

      // then
      EitherAssert.assertThat(result).isRight();
      ClusterConfigurationAssert.assertThatClusterTopology(
              result.get().apply(configWithoutPartition))
          .describedAs("Partition added at state bootstrapping")
          .member(localMemberId)
          .hasPartitionSatisfying(
              1,
              state ->
                  PartitionStateAssert.assertThat(state)
                      .hasConfig(partitionConfig)
                      .hasState(State.BOOTSTRAPPING)
                      .hasPriority(priority));
    }

    @Test
    void shouldBootstrapWithConfigFromPartition1WhenNoConfigGiven() {
      // given
      final DynamicPartitionConfig nonEmptyPartitionConfig =
          DynamicPartitionConfig.init()
              .updateExporting(
                  new ExportingConfig(
                      ExportingState.EXPORTING,
                      Map.of(
                          "exporter",
                          new ExporterState(
                              1, ExporterState.State.ENABLED, Optional.of("config")))));
      final ClusterConfiguration clusterConfiguration =
          ClusterConfiguration.init()
              .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
              .updateMember(
                  localMemberId,
                  m -> m.addPartition(1, PartitionState.active(priority, nonEmptyPartitionConfig)));

      // when
      final var result =
          new PartitionBootstrapApplier(
                  new PartitionBootstrapOperation(
                      localMemberId, 2, priority, Optional.empty(), false),
                  partitionChangeExecutor)
              .init(clusterConfiguration);

      // then
      EitherAssert.assertThat(result).isRight();
      ClusterConfigurationAssert.assertThatClusterTopology(result.get().apply(clusterConfiguration))
          .describedAs("Partition added at state bootstrapping")
          .member(localMemberId)
          .hasPartitionSatisfying(
              2,
              state ->
                  PartitionStateAssert.assertThat(state)
                      .hasConfig(nonEmptyPartitionConfig)
                      .hasState(State.BOOTSTRAPPING)
                      .hasPriority(priority));
    }
  }

  @Nested
  final class ApplyWithoutSnapshot extends Apply {
    public ApplyWithoutSnapshot() {
      super(false);
    }
  }

  @Nested
  final class ApplyWithSnapshot extends Apply {
    public ApplyWithSnapshot() {
      super(true);
    }
  }

  abstract class Apply {
    private PartitionBootstrapApplier partitionBootstrapApplier;
    private ClusterConfiguration operationInitialized;
    private final boolean initFromSnapshot;

    public Apply(final boolean initFromSnapshot) {
      this.initFromSnapshot = initFromSnapshot;
    }

    @BeforeEach
    void init() {
      final var initialTopology =
          ClusterConfiguration.init()
              .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
              .updateMember(
                  localMemberId,
                  m -> m.addPartition(1, PartitionState.active(priority, partitionConfig)));
      partitionBootstrapApplier =
          new PartitionBootstrapApplier(
              partitionId, priority, localMemberId, initFromSnapshot, partitionChangeExecutor);
      // when
      operationInitialized =
          partitionBootstrapApplier.init(initialTopology).get().apply(initialTopology);
    }

    @Test
    void shouldFailFutureIfBootstrappingFailed() {
      // given
      when(partitionChangeExecutor.bootstrap(
              partitionId, priority, partitionConfig, initFromSnapshot))
          .thenReturn(CompletableActorFuture.completedExceptionally(new RuntimeException("FAIL")));

      // when
      final var result = partitionBootstrapApplier.apply();

      // then
      assertThat(result).failsWithin(Duration.ofMillis(100));
    }

    @Test
    void shouldUpdateStateToActive() {
      // given
      when(partitionChangeExecutor.bootstrap(
              partitionId, priority, partitionConfig, initFromSnapshot))
          .thenReturn(CompletableActorFuture.completed(null));

      // when
      final var result = partitionBootstrapApplier.apply();

      // then
      assertThat(result).succeedsWithin(Duration.ofMillis(100));
      ClusterConfigurationAssert.assertThatClusterTopology(
              result.join().apply(operationInitialized))
          .member(localMemberId)
          .hasPartitionSatisfying(
              partitionId,
              state ->
                  PartitionStateAssert.assertThat(state)
                      .hasConfig(partitionConfig)
                      .hasState(State.ACTIVE)
                      .hasPriority(priority));
    }
  }
}
