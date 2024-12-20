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
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.PartitionStateAssert;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
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
                  partitionId, priority, localMemberId, partitionChangeExecutor)
              .init(configurationWithActivePartition);

      // then
      EitherAssert.assertThat(result).isLeft().left().isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectIfPartitionIdIsNotContiguous() {
      // when
      final var result =
          new PartitionBootstrapApplier(
                  partitionId + 1, priority, localMemberId, partitionChangeExecutor)
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
                  partitionId, priority, localMemberId, partitionChangeExecutor)
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

      // when
      final var result =
          new PartitionBootstrapApplier(
                  partitionId, priority, localMemberId, partitionChangeExecutor)
              .init(topologyWithPartitionBootstrapping);

      // then
      EitherAssert.assertThat(result).isRight();
      assertThat(result.get().apply(topologyWithPartitionBootstrapping))
          .describedAs("No change to the topology")
          .isEqualTo(topologyWithPartitionBootstrapping);
    }

    @Test
    void shouldUpdatePartitionStateToBootstrapping() {
      // when
      final var result =
          new PartitionBootstrapApplier(
                  partitionId, priority, localMemberId, partitionChangeExecutor)
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
                  new ExportersConfig(
                      Map.of(
                          "exporter",
                          new ExporterState(
                              1, ExporterState.State.ENABLED, Optional.of("config")))));

      // when
      final var result =
          new PartitionBootstrapApplier(
                  1, priority, localMemberId, Optional.of(partitionConfig), partitionChangeExecutor)
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
                  new ExportersConfig(
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
                  2, priority, localMemberId, Optional.empty(), partitionChangeExecutor)
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
  final class Apply {
    private PartitionBootstrapApplier partitionBootstrapApplier;
    private ClusterConfiguration operationInitialized;

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
              partitionId, priority, localMemberId, partitionChangeExecutor);
      // when
      operationInitialized =
          partitionBootstrapApplier.init(initialTopology).get().apply(initialTopology);
    }

    @Test
    void shouldFailFutureIfBootstrappingFailed() {
      // given
      when(partitionChangeExecutor.bootstrap(partitionId, priority, partitionConfig))
          .thenReturn(CompletableActorFuture.completedExceptionally(new RuntimeException("FAIL")));

      // when
      final var result = partitionBootstrapApplier.apply();

      // then
      assertThat(result).failsWithin(Duration.ofMillis(100));
    }

    @Test
    void shouldUpdateStateToActive() {
      // given
      when(partitionChangeExecutor.bootstrap(partitionId, priority, partitionConfig))
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
