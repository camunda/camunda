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
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.CurrentClusterConfigurationInitializer.StaticInitializer;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.BrokerState.State;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the new multi-partition-group apply loop in {@link ClusterConfigurationManagerImpl}. */
final class ClusterConfigurationManagerImplNewConfigTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @TempDir private Path tmp;

  private ClusterConfigurationManagerImpl newManager(final MemberId localMemberId) {
    final var persisted =
        PersistedCurrentClusterConfiguration.ofFile(
            tmp.resolve("config-" + localMemberId.id() + ".meta"), new ProtoBufSerializer());
    final var manager =
        new ClusterConfigurationManagerImpl(
            executor,
            localMemberId,
            persisted,
            new TopologyManagerMetrics(new SimpleMeterRegistry()),
            Duration.ofMillis(1),
            Duration.ofMillis(1));
    manager.setCurrentConfigurationGossiper(ignored -> {});
    manager.registerGlobalChangeAppliers(
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor()));
    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopClusterChangeExecutor(),
            new NoopModeChangeExecutor()));
    return manager;
  }

  private CurrentClusterConfiguration configuration(final ClusterConfigurationManagerImpl manager) {
    return manager.getMultiConfiguration().join();
  }

  @Test
  void shouldApplyGlobalOperationForLocalMember() {
    // given — the local member joins via a single-phase (global) plan
    final var manager = newManager(MEMBER_1);
    manager
        .updateMultiConfiguration(
            c -> c.initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_1))))))
        .join();

    // then — the global change was applied: the member is ACTIVE and the global plan drained
    final var config = configuration(manager);
    assertThat(config.globalConfiguration().getMember(MEMBER_1).state()).isEqualTo(State.ACTIVE);
    assertThat(config.globalConfiguration().hasPendingChanges()).isFalse();
  }

  @Test
  void shouldApplyPartitionGroupOperationForLocalMember() {
    // given — partition 1 has two replicas (members 0 and 1); the local member 0 leaves it
    final var manager = newManager(MEMBER_0);
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
    final var seeded =
        new CurrentClusterConfiguration(
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
    manager.updateMultiConfiguration(ignored -> seeded).join();

    // when — a partition-group phase removes member 0's replica (min allowed replicas = 1)
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        new PartitionGroupParallelPhase(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP,
                                List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1)))))))
        .join();

    // then — the group plan drained; member 0 (now with no partitions) is removed, member 1 stays
    final var defaultGroup =
        configuration(manager).partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasPendingChanges()).isFalse();
    assertThat(defaultGroup.hasMember(MEMBER_0)).isFalse();
    assertThat(defaultGroup.hasMember(MEMBER_1)).isTrue();
  }

  @Test
  void shouldNotApplyOperationForOtherMember() {
    // given — a plan whose only operation targets a different member
    final var manager = newManager(MEMBER_0);
    manager
        .updateMultiConfiguration(
            c -> c.initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_1))))))
        .join();

    // then — the local member (0) does not apply member 1's operation; it stays pending
    final var config = configuration(manager);
    assertThat(config.globalConfiguration().hasPendingChanges()).isTrue();
    assertThat(config.globalConfiguration().hasMember(MEMBER_1)).isFalse();
  }

  @Test
  void shouldAdvanceAndCompleteMultiPhasePlanAsCoordinator() {
    // given — member 0 is the lowest-id member, so it is the coordinator responsible for
    // advancing the plan once each phase's changes have drained
    final var manager = newManager(MEMBER_0);

    // when — a two-phase plan is initiated: member 0 joins, then leaves, each phase containing an
    // operation for the local member
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0))),
                        new GlobalPhase(List.of(new MemberLeaveOperation(MEMBER_0))))))
        .join();

    // then — both phases were applied and the coordinator advanced/completed the plan on its own
    final var config = configuration(manager);
    assertThat(config.phasedChangeState().pending()).isEmpty();
    final var lastChange = config.phasedChangeState().lastChange();
    assertThat(lastChange).isPresent();
    assertThat(lastChange.get().status()).isEqualTo(PhasedChangePlanStatus.COMPLETED);
  }

  @Test
  void shouldNotAdvancePhaseWhenLocalMemberIsNotCoordinator() {
    // given — member 1 is not the lowest-id member (member 0 is present too), so it is not the
    // coordinator; both members start ACTIVE with no partitions assigned
    final var manager = newManager(MEMBER_1);
    final var seeded =
        new CurrentClusterConfiguration(
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
            Map.of(),
            PhasedChangeState.empty());
    manager.updateMultiConfiguration(ignored -> seeded).join();

    // when — a two-phase plan's first phase only contains an operation for the local member
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        new GlobalPhase(List.of(new MemberLeaveOperation(MEMBER_1))),
                        new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_1))))))
        .join();

    // then — the first phase's operation was applied, but the plan is not advanced to phase 2
    // since the local member is not the coordinator
    final var config = configuration(manager);
    final var pending = config.phasedChangeState().pending();
    assertThat(pending).isPresent();
    assertThat(pending.get().currentPhaseIndex()).isZero();
    assertThat(config.globalConfiguration().hasPendingChanges()).isFalse();
  }

  @Test
  void shouldAdvanceFromGlobalPhaseToPartitionGroupPhase() {
    // given — member 0 is the coordinator; it is not yet part of the cluster or the default
    // group, which only has member 1 holding partition 1
    final var manager = newManager(MEMBER_0);
    final var group =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, partitionConfig)))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var seeded =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            new GlobalConfiguration(
                1,
                Optional.empty(),
                Map.of(MEMBER_1, new BrokerState(0, Instant.EPOCH, State.ACTIVE)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()),
            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, group),
            PhasedChangeState.empty());
    manager.updateMultiConfiguration(ignored -> seeded).join();

    // when — a global phase joins member 0 to the cluster, followed by a partition-group phase
    // that adds member 0 as a replica of partition 1
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0))),
                        new PartitionGroupParallelPhase(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP,
                                List.of(new PartitionJoinOperation(MEMBER_0, 1, 1)))))))
        .join();

    // then — the coordinator advanced from the global phase into the partition-group phase and
    // completed the plan once member 0 joined both the cluster and the partition-group replica set
    final var config = configuration(manager);
    assertThat(config.globalConfiguration().getMember(MEMBER_0).state()).isEqualTo(State.ACTIVE);
    final var defaultGroup = config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasMember(MEMBER_0)).isTrue();
    assertThat(defaultGroup.hasPendingChanges()).isFalse();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    final var lastChange = config.phasedChangeState().lastChange();
    assertThat(lastChange).isPresent();
    assertThat(lastChange.get().status()).isEqualTo(PhasedChangePlanStatus.COMPLETED);
  }

  @Test
  void shouldMergeAndApplyOperationOnGossipReceived() {
    // given — the local member (1) starts with an empty, initialized configuration
    final var manager = newManager(MEMBER_1);
    manager.updateMultiConfiguration(ignored -> CurrentClusterConfiguration.init()).join();

    // when — a configuration received via gossip carries a pending plan joining the local member
    final var received =
        CurrentClusterConfiguration.init()
            .initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_1)))));
    manager.onGossipReceivedCurrent(received);

    // then — the merge is persisted locally and the local member's join operation is applied
    final var config = configuration(manager);
    assertThat(config.globalConfiguration().getMember(MEMBER_1).state()).isEqualTo(State.ACTIVE);
    assertThat(config.globalConfiguration().hasPendingChanges()).isFalse();
  }

  @Test
  void shouldStartFromStaticInitializer() {
    // given — a fresh (never-started) manager and a static configuration for two members
    final var manager = newManager(MEMBER_0);
    final var partition =
        new PartitionMetadata(
            new PartitionId(CurrentClusterConfiguration.DEFAULT_GROUP, 1),
            Set.of(MEMBER_0, MEMBER_1),
            Map.of(MEMBER_0, 1, MEMBER_1, 1),
            1,
            MEMBER_0);
    final var staticConfiguration =
        new StaticConfiguration(
            new ControllablePartitionDistributor().withPartitions(Set.of(partition)),
            Set.of(MEMBER_0, MEMBER_1),
            MEMBER_0,
            List.of(new PartitionId(CurrentClusterConfiguration.DEFAULT_GROUP, 1)),
            1,
            partitionConfig,
            "cluster-x");

    // when
    manager.start(new StaticInitializer(staticConfiguration)).join();

    // then — the generated multi-group configuration was persisted and is retrievable
    final var config = configuration(manager);
    assertThat(config.globalConfiguration().members().keySet())
        .containsExactlyInAnyOrder(MEMBER_0, MEMBER_1);
    assertThat(config.globalConfiguration().clusterId()).contains("cluster-x");
    assertThat(config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).members().keySet())
        .containsExactlyInAnyOrder(MEMBER_0, MEMBER_1);
  }

  @Test
  void shouldFailToStartWhenGeneratedConfigurationHasNoMembers() {
    // given — a static configuration with no cluster members at all
    final var manager = newManager(MEMBER_0);
    final var staticConfiguration =
        new StaticConfiguration(
            new ControllablePartitionDistributor().withPartitions(Set.of()),
            Set.of(),
            MEMBER_0,
            List.of(),
            1,
            partitionConfig,
            null);

    // when
    final var startFuture = manager.start(new StaticInitializer(staticConfiguration));

    // then
    assertThat(startFuture).failsWithin(Duration.ofSeconds(1));
  }
}
