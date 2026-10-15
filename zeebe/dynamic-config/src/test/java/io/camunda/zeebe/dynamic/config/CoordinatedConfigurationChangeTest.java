/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinatorImpl;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor.NoopRestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.BrokerState.State;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that a coordinator bases every change on the {@link ClusterConfigurationStore}, writes
 * it there before applying it locally, and is rejected when another coordinator changed the store
 * in the meantime; and that the coordinator is the lowest alive member.
 */
final class CoordinatedConfigurationChangeTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");

  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final ClusterConfigurationStore store = ClusterConfigurationStore.inMemory();

  @TempDir private Path tmp;

  private ClusterConfigurationManagerImpl manager;
  private ConfigurationChangeCoordinatorImpl coordinator;

  @Test
  void shouldWriteChangeAndItsCompletionToStore() {
    // given
    wire(MEMBER_0, Set.of(MEMBER_0, MEMBER_1), store, twoMemberCluster());

    // when
    final var result = coordinator.applyOperations(leavePartition(MEMBER_0)).join();

    // then - the plan's completion was written by the coordinator as well
    final var stored = store.read().join().orElseThrow();
    assertThat(stored.phasedChangeState().nextId()).isEqualTo(result.changeId() + 1);
    assertThat(stored.phasedChangeState().pending()).isEmpty();
    assertThat(stored.phasedChangeState().lastChange())
        .hasValueSatisfying(
            last -> {
              assertThat(last.id()).isEqualTo(result.changeId());
              assertThat(last.status()).isEqualTo(PhasedChangePlanStatus.COMPLETED);
            });
    assertThat(localConfiguration().phasedChangeState()).isEqualTo(stored.phasedChangeState());
  }

  @Test
  void shouldContinueFromChangesOfPreviousCoordinator() {
    // given - a previous coordinator issued changes which this member has not yet received
    final var seed = twoMemberCluster();
    final var previousChanges =
        new CurrentClusterConfiguration(
            seed.version(),
            seed.globalConfiguration(),
            seed.partitionGroups(),
            new PhasedChangeState(5, Map.of(), List.of()));
    store.compareAndSet(ClusterConfigurationStore.ABSENT, previousChanges).join();
    wire(MEMBER_0, Set.of(MEMBER_0, MEMBER_1), store, seed);

    // when
    final var result = coordinator.applyOperations(leavePartition(MEMBER_1)).join();

    // then
    assertThat(result.changeId()).isEqualTo(5);
    assertThat(store.read().join().orElseThrow().phasedChangeState().nextId()).isEqualTo(6);
  }

  @Test
  void shouldRejectChangeWhenAnotherCoordinatorChangedStoreConcurrently() {
    // given - another coordinator commits a change between this coordinator's read and write
    final var racingStore =
        new ClusterConfigurationStore() {
          @Override
          public CompletableFuture<Optional<CurrentClusterConfiguration>> read() {
            return store.read();
          }

          @Override
          public CompletableFuture<Void> compareAndSet(
              final long expectedNextId, final CurrentClusterConfiguration configuration) {
            store.compareAndSet(expectedNextId, configuration).join();
            return store.compareAndSet(expectedNextId, configuration);
          }
        };
    wire(MEMBER_0, Set.of(MEMBER_0, MEMBER_1), racingStore, twoMemberCluster());
    final var nextIdBefore = localConfiguration().phasedChangeState().nextId();

    // when
    final var result = coordinator.applyOperations(leavePartition(MEMBER_1));

    // then
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseInstanceOf(ConcurrentModificationException.class);
    assertThat(localConfiguration().phasedChangeState().nextId()).isEqualTo(nextIdBefore);
  }

  @Test
  void shouldCoordinateWhenLowerMembersAreNotAlive() {
    // given
    wire(MEMBER_1, Set.of(MEMBER_1), store, twoMemberCluster());

    // when
    final var result = coordinator.applyOperations(leavePartition(MEMBER_1));

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));
  }

  @Test
  void shouldNotCoordinateWhenLowerMemberIsAlive() {
    // given
    wire(MEMBER_1, Set.of(MEMBER_0, MEMBER_1), store, twoMemberCluster());

    // when
    final var result = coordinator.applyOperations(leavePartition(MEMBER_1));

    // then
    assertThat(result).failsWithin(Duration.ofSeconds(5));
    assertThat(store.read().join()).isEmpty();
  }

  private void wire(
      final MemberId localMemberId,
      final Set<MemberId> aliveMembers,
      final ClusterConfigurationStore configurationStore,
      final CurrentClusterConfiguration seed) {
    final var persisted =
        PersistedCurrentClusterConfiguration.ofFile(
            tmp.resolve("config-" + localMemberId.id() + ".meta"), new ProtoBufSerializer());
    manager =
        new ClusterConfigurationManagerImpl(
            executor,
            localMemberId,
            persisted,
            new TopologyManagerMetrics(new SimpleMeterRegistry()),
            configurationStore,
            () -> aliveMembers);
    manager.setCurrentConfigurationGossiper(ignored -> {});
    manager.registerGlobalChangeAppliers(
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor()));
    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));
    coordinator =
        new ConfigurationChangeCoordinatorImpl(
            manager, localMemberId, executor, () -> aliveMembers);
    manager.updateMultiConfiguration(ignored -> seed).join();
  }

  private static ConfigurationChangeRequest leavePartition(final MemberId member) {
    return current ->
        Either.right(
            CurrentClusterConfiguration.toPhases(
                List.of(new PartitionLeaveOperation(member, 1, 1))));
  }

  private CurrentClusterConfiguration localConfiguration() {
    return manager.getMultiConfiguration().join();
  }

  /** Two active members, both replicating partition 1 in the default group. */
  private CurrentClusterConfiguration twoMemberCluster() {
    final var group =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_0,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(2, partitionConfig))),
                MEMBER_1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, partitionConfig)))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(
                MEMBER_0, new BrokerState(0, Instant.EPOCH, State.ACTIVE),
                MEMBER_1, new BrokerState(0, Instant.EPOCH, State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()),
        Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, group),
        PhasedChangeState.empty());
  }
}
