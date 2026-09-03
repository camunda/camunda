/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.OperationGraph.PlannedOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CurrentClusterConfigurationTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");

  private static PartitionState partition() {
    return PartitionState.active(1, DynamicPartitionConfig.init());
  }

  private static MemberState activeMember() {
    return new MemberState(1, Instant.EPOCH, MemberState.State.ACTIVE, Map.of(1, partition()));
  }

  private static BrokerState broker(final long version, final BrokerState.State state) {
    return new BrokerState(version, Instant.EPOCH, state);
  }

  private static BrokerPartitionState brokerPartition(final int partitionId) {
    return new BrokerPartitionState(
        1, Instant.EPOCH, Map.of(partitionId, partition()), Mode.PROCESSING);
  }

  private static BrokerPartitionState brokerPartition(final int partitionId, final int priority) {
    return new BrokerPartitionState(
        1,
        Instant.EPOCH,
        Map.of(partitionId, PartitionState.active(priority, DynamicPartitionConfig.init())),
        Mode.PROCESSING);
  }

  private static PartitionGroupConfiguration group(
      final long version, final Map<MemberId, BrokerPartitionState> members) {
    return new PartitionGroupConfiguration(
        version, 0, members, Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static GlobalConfiguration global(
      final long version, final Map<MemberId, BrokerState> members) {
    return new GlobalConfiguration(
        version, Optional.empty(), members, Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static CurrentClusterConfiguration config(
      final GlobalConfiguration global,
      final Map<String, PartitionGroupConfiguration> groups,
      final PhasedChangeState phasedChangeState) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION, global, groups, phasedChangeState);
  }

  /**
   * Completes every operation of the global configuration's pending change and clears the drained
   * plan — what the broker applying them would leave behind, without any of their effects on member
   * state.
   */
  private static CurrentClusterConfiguration drainGlobal(final CurrentClusterConfiguration config) {
    var current = config;
    while (current.globalConfiguration().hasPendingChanges()) {
      final var plan = current.globalConfiguration().pendingChanges().orElseThrow();
      final var next =
          plan.operations().keySet().stream().filter(plan::isRunnable).findFirst().orElseThrow();
      current =
          current.updateGlobalConfiguration(
              g -> g.completeOperation(next, UnaryOperator.identity()));
    }
    return current.updateGlobalConfiguration(GlobalConfiguration::completeGraphChangeIfDrained);
  }

  /**
   * Completes every operation of {@code groupId}'s pending change, one runnable operation at a
   * time, and finishes the change — what the broker applying the operations would leave behind,
   * without any of their effects on member state.
   */
  private static CurrentClusterConfiguration drainGroup(
      final CurrentClusterConfiguration config, final String groupId) {
    var current = config;
    while (current.partitionGroup(groupId).hasPendingChanges()) {
      final var plan = current.partitionGroup(groupId).pendingChanges().orElseThrow();
      final var next =
          plan.operations().keySet().stream().filter(plan::isRunnable).findFirst().orElseThrow();
      current =
          current.updatePartitionGroupConfig(
              groupId,
              group ->
                  group
                      .completeOperation(next, UnaryOperator.identity())
                      .completeGraphChangeIfDrained());
    }
    return current;
  }

  @Nested
  class OfDefault {

    @Test
    void shouldSplitLifecycleAndPartitionState() {
      // given — an active member with a partition, and a member that has left with no partitions
      final var active =
          new MemberState(4, Instant.EPOCH, MemberState.State.ACTIVE, Map.of(1, partition()));
      final var left = new MemberState(2, Instant.EPOCH, MemberState.State.LEFT, Map.of());
      final var legacy =
          ClusterConfiguration.builder()
              .version(7)
              .members(Map.of(MEMBER_0, active, MEMBER_1, left))
              .clusterId(Optional.of("cluster-x"))
              .partitionDistributorConfig(Optional.of(new RoundRobinConfig()))
              .incarnationNumber(3)
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — every member is in the global config with its lifecycle state
      assertThat(migrated.globalConfiguration().members()).containsOnlyKeys(MEMBER_0, MEMBER_1);
      assertThat(migrated.globalConfiguration().getMember(MEMBER_0))
          .isEqualTo(broker(4, BrokerState.State.ACTIVE));
      assertThat(migrated.globalConfiguration().getMember(MEMBER_1))
          .isEqualTo(broker(2, BrokerState.State.LEFT));
      // cluster-level settings live on the global config
      assertThat(migrated.globalConfiguration().clusterId()).contains("cluster-x");
      assertThat(migrated.globalConfiguration().partitionDistributorConfig())
          .contains(new RoundRobinConfig());

      // and — only members with partitions are in the default group
      final var defaultGroup = migrated.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
      assertThat(defaultGroup.members()).containsOnlyKeys(MEMBER_0);
      assertThat(defaultGroup.getMember(MEMBER_0).mode()).isEqualTo(Mode.PROCESSING);
      assertThat(defaultGroup.incarnationNumber()).isEqualTo(3);
    }

    @Test
    void shouldMigrateAGraphChangeKeepingItsEdges() {
      // given — a legacy view carrying a graph change, which is what projecting a partition group
      // in-process produces: one operation waits for the first, the third for nothing
      final var builder = OperationGraph.builder();
      final var first = builder.add(new PartitionJoinOperation(MEMBER_0, 1, 1, true));
      builder.add(new PartitionLeaveOperation(MEMBER_0, 2, 1), Set.of(first));
      builder.add(new PartitionJoinOperation(MEMBER_1, 1, 1, true));
      final var graph = builder.build();
      final var legacy =
          ClusterConfiguration.builder()
              .version(2)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(DependencyChangePlan.init(4, graph)))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — the change is migrated as the graph it is, edges and all. Flattening it into a
      // sequential phase would order the third operation behind the other two, which nothing in
      // the change asked for.
      final var phase = migrated.phasedChangeState().onlyPending().currentPhase();
      assertThat(phase)
          .isEqualTo(
              new PartitionGroupPhase(Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, graph)));
    }

    @Test
    void shouldMigrateAClusterWideGraphChangeAsAGlobalPhase() {
      // given — a legacy view carrying a graph of cluster-wide operations, which is what projecting
      // the global configuration produces while it is running a change
      final var builder = OperationGraph.builder();
      final var first = builder.add(new MemberJoinOperation(MEMBER_0));
      builder.add(new MemberLeaveOperation(MEMBER_1), Set.of(first));
      final var legacy =
          ClusterConfiguration.builder()
              .version(2)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(DependencyChangePlan.init(4, builder.build())))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — the operations go into a global phase, in the order the graph runs them. Putting
      // them in a partition-group phase instead would hand cluster-wide operations to a group,
      // which cannot run them at all.
      assertThat(migrated.phasedChangeState().onlyPending().currentPhase())
          .isEqualTo(
              new GlobalPhase(
                  List.of(new MemberJoinOperation(MEMBER_0), new MemberLeaveOperation(MEMBER_1))));
    }

    @Test
    void shouldRejectMigratingAGraphMixingClusterWideAndGroupOperations() {
      // given — a graph holding one of each kind. No sub-configuration can produce this: the global
      // configuration holds only cluster-wide operations and a group only its own.
      final var builder = OperationGraph.builder();
      builder.add(new MemberJoinOperation(MEMBER_0));
      builder.add(new PartitionJoinOperation(MEMBER_0, 1, 1, true));
      final var legacy =
          ClusterConfiguration.builder()
              .version(2)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(DependencyChangePlan.init(4, builder.build())))
              .build();

      // when / then — rejected rather than split across two phases, which would invent an ordering
      // between the two kinds that the graph never declared
      assertThatThrownBy(() -> CurrentClusterConfiguration.fromLegacy(legacy))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("partly cluster-wide");
    }

    @Test
    void shouldMigrateOnlyWhatIsLeftOfAGraphChange() {
      // given — a graph change whose first operation has already completed
      final var builder = OperationGraph.builder();
      final var first = builder.add(new PartitionJoinOperation(MEMBER_0, 1, 1, true));
      final var second = builder.add(new PartitionLeaveOperation(MEMBER_0, 2, 1), Set.of(first));
      final var plan =
          new DependencyChangePlan(
              4,
              ClusterChangePlan.Status.IN_PROGRESS,
              Instant.EPOCH,
              builder.build(),
              new TreeMap<>(Map.of(first, Instant.EPOCH)));
      final var legacy =
          ClusterConfiguration.builder()
              .version(2)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(plan))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — only the outstanding operation is migrated, with its dependency on the completed one
      // dropped. A phase carries no progress, so carrying the completed operation over would put it
      // back on the queue to run a second time, and keeping the edge would name an operation the
      // migrated graph no longer contains.
      final var phase = migrated.phasedChangeState().onlyPending().currentPhase();
      assertThat(phase)
          .isEqualTo(
              new PartitionGroupPhase(
                  Map.of(
                      CurrentClusterConfiguration.DEFAULT_GROUP,
                      OperationGraph.of(
                          new TreeMap<>(
                              Map.of(
                                  second,
                                  PlannedOperation.of(
                                      new PartitionLeaveOperation(MEMBER_0, 2, 1))))))));
    }

    @Test
    void shouldKeepEachNodesTargetGroupWhenMigratingWhatIsLeft() {
      // given — a graph whose nodes name the groups they target, with the first one completed
      final var builder = OperationGraph.builder();
      final var first =
          builder.add(
              new PartitionJoinOperation(MEMBER_0, 1, 1, true), Set.of(), Optional.of("tenant-a"));
      final var second =
          builder.add(
              new PartitionJoinOperation(MEMBER_0, 2, 1, true),
              Set.of(first),
              Optional.of("tenant-b"));
      final var plan =
          new DependencyChangePlan(
              4,
              ClusterChangePlan.Status.IN_PROGRESS,
              Instant.EPOCH,
              builder.build(),
              new TreeMap<>(Map.of(first, Instant.EPOCH)));
      final var legacy =
          ClusterConfiguration.builder()
              .version(2)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(plan))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — the surviving node keeps the group it names. Dropping it would silently retarget the
      // operation at whichever sub-configuration happens to hold the migrated graph.
      final var phase =
          (PartitionGroupPhase) migrated.phasedChangeState().onlyPending().currentPhase();
      assertThat(
              phase
                  .groupGraphs()
                  .get(CurrentClusterConfiguration.DEFAULT_GROUP)
                  .operations()
                  .get(second)
                  .groupId())
          .contains("tenant-b");
    }

    @Test
    void shouldMigrateNoPlanWhenAGraphChangeHasFullyDrained() {
      // given — a graph change with every operation completed but the plan not yet cleared
      final var builder = OperationGraph.builder();
      final var only = builder.add(new PartitionJoinOperation(MEMBER_0, 1, 1, true));
      final var plan =
          new DependencyChangePlan(
              4,
              ClusterChangePlan.Status.IN_PROGRESS,
              Instant.EPOCH,
              builder.build(),
              new TreeMap<>(Map.of(only, Instant.EPOCH)));
      final var legacy =
          ClusterConfiguration.builder()
              .version(2)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(plan))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — nothing is left to run, so no plan is migrated rather than an empty graph, which
      // OperationGraph rejects outright
      assertThat(migrated.phasedChangeState().pending()).isEmpty();
    }

    @Test
    void shouldMapRecoveringMemberToActiveBrokerWithRecoveringMode() {
      // given — a recovering member with a partition
      final var recovering =
          new MemberState(5, Instant.EPOCH, MemberState.State.RECOVERING, Map.of(1, partition()));
      final var legacy =
          ClusterConfiguration.builder().version(1).members(Map.of(MEMBER_0, recovering)).build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — lifecycle is ACTIVE, per-group mode is RECOVERING
      assertThat(migrated.globalConfiguration().getMember(MEMBER_0).state())
          .isEqualTo(BrokerState.State.ACTIVE);
      assertThat(
              migrated
                  .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                  .getMember(MEMBER_0)
                  .mode())
          .isEqualTo(Mode.RECOVERING);
    }

    @Test
    void shouldConvertMixedPendingOperationsIntoOrderedPhases() {
      // given — a legacy pending plan mixing global and partition ops in one flat list
      final var memberJoin = new MemberJoinOperation(MEMBER_0);
      final var partitionJoin = new PartitionJoinOperation(MEMBER_0, 1, 1, true);
      final var partitionLeave = new PartitionLeaveOperation(MEMBER_0, 1, 1);
      final var memberLeave = new MemberLeaveOperation(MEMBER_0);
      final var pending =
          ClusterChangePlan.init(
              9, List.of(memberJoin, partitionJoin, partitionLeave, memberLeave));
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(pending))
              .build();

      // when — fromLegacy() itself is a pure conversion; activatePendingPhase() is the explicit
      // step a one-time-migration caller takes to start driving the pending plan (mirrors what
      // PersistedCurrentClusterConfiguration does when upgrading an on-disk v1 file)
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy).activatePendingPhase();

      // then — the ops become three phases in order: [global], [default: 2 partition ops], [global]
      final var plan = migrated.phasedChangeState().onlyPending();
      assertThat(plan.id()).isEqualTo(9);
      assertThat(plan.currentPhaseIndex()).isZero();
      assertThat(plan.phases())
          .containsExactly(
              new GlobalPhase(List.of(memberJoin)),
              PartitionGroupPhase.sequential(
                  Map.of(
                      CurrentClusterConfiguration.DEFAULT_GROUP,
                      List.of(partitionJoin, partitionLeave))),
              new GlobalPhase(List.of(memberLeave)));
      // and — phase 0 (the first global phase) is already activated, like initPlan does
      assertThat(migrated.globalConfiguration().hasPendingChanges()).isTrue();
      assertThat(migrated.globalConfiguration().pendingChanges().orElseThrow().pendingOperations())
          .containsExactly(memberJoin);
      // and — the pending change is NOT left on the default group, since phase 0 is global
      assertThat(
              migrated.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).pendingChanges())
          .isEmpty();
    }

    @Test
    void shouldActivateFirstPhaseOnDefaultGroupWhenItIsAPartitionPhase() {
      // given — a legacy pending plan whose first (and only) run of operations is partition-scoped
      final var join = new PartitionJoinOperation(MEMBER_0, 1, 1, true);
      final var leave = new PartitionLeaveOperation(MEMBER_0, 1, 1);
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(ClusterChangePlan.init(2, List.of(join, leave))))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy).activatePendingPhase();

      // then — phase 0 is already activated on the default group, mirroring initPlan's semantics
      final var defaultGroup = migrated.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
      assertThat(defaultGroup.hasPendingChanges()).isTrue();
      assertThat(defaultGroup.pendingChanges().orElseThrow().pendingOperations())
          .containsExactly(join, leave);
      // and — the global configuration is untouched, since phase 0 targets the default group only
      assertThat(migrated.globalConfiguration().hasPendingChanges()).isFalse();
    }

    @Test
    void shouldAllowActivatingNextPhaseAfterMigration() {
      // given — a migrated multi-phase plan whose phase 0 is already activated
      final var memberJoin = new MemberJoinOperation(MEMBER_0);
      final var partitionJoin = new PartitionJoinOperation(MEMBER_0, 1, 1, true);
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(
                  Optional.of(ClusterChangePlan.init(2, List.of(memberJoin, partitionJoin))))
              .build();
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy).activatePendingPhase();

      // when — advance to phase 1, exactly as would happen for a plan started via initPlan
      final var advanced =
          migrated.activateNextPhase(migrated.phasedChangeState().onlyPending().id());

      // then — phase 1 (the default-group phase) is now activated
      assertThat(advanced.phasedChangeState().onlyPending().currentPhaseIndex()).isEqualTo(1);
      assertThat(
              advanced.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).pendingChanges())
          .isPresent();
    }

    @Test
    void shouldNotFailMigrationWhenNoPendingPlanExists() {
      // given — no pending plan at all
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — nothing is activated anywhere
      assertThat(migrated.phasedChangeState().pending()).isEmpty();
      assertThat(migrated.globalConfiguration().hasPendingChanges()).isFalse();
      assertThat(
              migrated
                  .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                  .hasPendingChanges())
          .isFalse();
    }

    @Test
    void shouldNotActivateThePendingPlanOnItsOwn() {
      // given — a legacy pending plan whose first run of operations is partition-scoped
      final var join = new PartitionJoinOperation(MEMBER_0, 1, 1, true);
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(ClusterChangePlan.init(2, List.of(join))))
              .build();

      // when — fromLegacy() alone, without the explicit activatePendingPhase() step
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — the plan is recorded (phase 0 exists) but not activated into the default group.
      // fromLegacy() must stay a pure, repeatable conversion: it is also called on every gossip
      // update by BrokerTopologyManagerImpl#onClusterConfigurationUpdated(ClusterConfiguration)
      // purely for read-only topology reporting. If it activated the phase itself, every such call
      // would call startConfigurationChange again on a freshly-built (never-"pending") default
      // group, endlessly restarting an already in-progress plan from scratch — this was a real
      // regression caught by ExporterEnableTest once a broker/gateway kept re-deriving its topology
      // from repeated legacy gossip.
      assertThat(migrated.phasedChangeState().pending()).isNotEmpty();
      assertThat(
              migrated
                  .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                  .hasPendingChanges())
          .isFalse();

      // and — calling fromLegacy() again on the same input is idempotent (no growing version)
      final var migratedAgain = CurrentClusterConfiguration.fromLegacy(legacy);
      assertThat(migratedAgain.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).version())
          .isEqualTo(migrated.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).version());
    }

    @Test
    void shouldConvertLegacyLastChangeIntoPhasedChangeState() {
      // given — a legacy last completed change
      final var last =
          new CompletedChange(3, ClusterChangePlan.Status.FAILED, Instant.EPOCH, Instant.EPOCH);
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .lastChange(Optional.of(last))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — the last change moves to the PhasedChangeState, status preserved
      final var lastChange = migrated.phasedChangeState().lastChange().orElseThrow();
      assertThat(lastChange.id()).isEqualTo(3);
      assertThat(lastChange.status()).isEqualTo(PhasedChangePlanStatus.FAILED);
      // and — not left on the default group
      assertThat(migrated.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).lastChange())
          .isEmpty();
    }

    @Test
    void shouldThrowWhenLegacyLastChangeIsInProgress() {
      // given — a legacy last change with a non-terminal status
      final var inProgress =
          new CompletedChange(
              3, ClusterChangePlan.Status.IN_PROGRESS, Instant.EPOCH, Instant.EPOCH);
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .lastChange(Optional.of(inProgress))
              .build();

      // when / then
      assertThatThrownBy(() -> CurrentClusterConfiguration.fromLegacy(legacy))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldMigrateEmptyLegacyConfig() {
      // given — a fresh legacy config with no members
      final var legacy = ClusterConfiguration.init();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — an empty global config and an empty default group
      assertThat(migrated.globalConfiguration().members()).isEmpty();
      assertThat(migrated.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).members())
          .isEmpty();
    }

    @Test
    void shouldAssignRestoredPlanIdToMigratedRestorePlan() {
      // given — a legacy restore plan uses the negative sentinel id (RESTORE_CHANGE_ID = -2),
      // which cannot be preserved as-is (PhasedChangePlan requires a non-negative id)
      final var restore =
          ClusterChangePlan.initForRestore(List.of(new DeleteHistoryOperation(MEMBER_0)));
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(restore))
              .build();

      // when — migration must not fail on the non-positive id
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — the pending plan is assigned the new model's own restore sentinel id
      final var plan = migrated.phasedChangeState().onlyPending();
      assertThat(plan.id()).isEqualTo(PhasedChangePlan.RESTORED_PLAN_ID);
      assertThat(plan.hasRestorePlanId()).isTrue();
    }

    @Test
    void shouldRejectRestorePlanMigratedAlongsideAPriorCompletedChange() {
      // given — a restore plan is only ever produced alongside a freshly regenerated legacy
      // configuration (RestoreManager#restoreTopologyFile), so it should never carry a prior
      // completed change. Constructing that combination here simulates that assumption breaking.
      final var restore =
          ClusterChangePlan.initForRestore(List.of(new DeleteHistoryOperation(MEMBER_0)));
      final var priorChange =
          new CompletedChange(3, ClusterChangePlan.Status.COMPLETED, Instant.EPOCH, Instant.EPOCH);
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .lastChange(Optional.of(priorChange))
              .pendingChanges(Optional.of(restore))
              .build();

      // when / then — fails loudly with restore-specific context instead of only surfacing later
      // as PhasedChangeState's generic id-monotonicity violation
      assertThatThrownBy(() -> CurrentClusterConfiguration.fromLegacy(legacy))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("restore");
    }

    @Test
    void shouldDetectAfterRestoreOnMigratedRestorePlan() {
      // given — a legacy config that is isAfterRestore(): a restore plan with exactly one
      // pending UpdateRoutingState operation
      final var restore =
          ClusterChangePlan.initForRestore(
              List.of(new PartitionGroupOperation.UpdateRoutingState(MEMBER_0, Optional.empty())));
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(restore))
              .build();
      assertThat(legacy.isAfterRestore()).isTrue();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — the migrated configuration is detectable as post-restore too
      assertThat(migrated.isAfterRestore()).isTrue();
    }

    @Test
    void shouldNotDetectAfterRestoreForAnOrdinaryPendingUpdateRoutingState() {
      // given — a plain (non-restore) pending plan with the exact same single-op shape as a
      // restore plan: an admin-triggered updateRoutingState request produces this too, so
      // isAfterRestore() must not rely on operation shape alone
      final var pending =
          ClusterChangePlan.init(
              2,
              List.of(new PartitionGroupOperation.UpdateRoutingState(MEMBER_0, Optional.empty())));
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(pending))
              .build();
      assertThat(legacy.isAfterRestore()).isFalse();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then
      assertThat(migrated.isAfterRestore()).isFalse();
    }

    @Test
    void shouldNormalizeCompletedRestoreLastChangeIdSoNextPlanCanStart() {
      // given — a completed restore keeps the negative sentinel id in lastChange
      final var completedRestore =
          new CompletedChange(-2, ClusterChangePlan.Status.COMPLETED, Instant.EPOCH, Instant.EPOCH);
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .lastChange(Optional.of(completedRestore))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — the last change id is clamped to non-negative, so a new plan can still be started
      assertThat(migrated.phasedChangeState().lastChange().orElseThrow().id()).isEqualTo(0);
      final var withPlan =
          migrated.initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0)))));
      assertThat(withPlan.phasedChangeState().onlyPending().id()).isEqualTo(1);
    }

    @Test
    void shouldGroupConsecutiveGlobalOpsIntoSinglePhase() {
      // given — two consecutive global operations
      final var join = new MemberJoinOperation(MEMBER_0);
      final var leave = new MemberLeaveOperation(MEMBER_0);
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(ClusterChangePlan.init(2, List.of(join, leave))))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — a single GlobalPhase holds both, in order
      assertThat(migrated.phasedChangeState().onlyPending().phases())
          .containsExactly(new GlobalPhase(List.of(join, leave)));
    }

    @Test
    void shouldGroupConsecutivePartitionOpsIntoSinglePhase() {
      // given — a pending plan with only partition operations
      final var join = new PartitionJoinOperation(MEMBER_0, 1, 1, true);
      final var leave = new PartitionLeaveOperation(MEMBER_0, 1, 1);
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(ClusterChangePlan.init(2, List.of(join, leave))))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — a single default-group phase holds both, in order
      assertThat(migrated.phasedChangeState().onlyPending().phases())
          .containsExactly(
              PartitionGroupPhase.sequential(
                  Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, List.of(join, leave))));
    }

    @ParameterizedTest
    @EnumSource(
        value = ClusterChangePlan.Status.class,
        names = {"COMPLETED", "FAILED", "CANCELLED"})
    void shouldConvertEachTerminalLegacyLastChangeStatus(final ClusterChangePlan.Status status) {
      // given
      final var last = new CompletedChange(3, status, Instant.EPOCH, Instant.EPOCH);
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .lastChange(Optional.of(last))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — the status maps to the matching PhasedChangePlanStatus
      assertThat(migrated.phasedChangeState().lastChange().orElseThrow().status())
          .isEqualTo(PhasedChangePlanStatus.valueOf(status.name()));
    }

    @Test
    void shouldPreserveTimestampsDuringMigration() {
      // given — a pending plan and a last change with distinct timestamps
      final var pendingStartedAt = Instant.ofEpochSecond(300);
      final var pending =
          new ClusterChangePlan(
              7,
              1,
              ClusterChangePlan.Status.IN_PROGRESS,
              pendingStartedAt,
              List.of(),
              List.of(new DeleteHistoryOperation(MEMBER_0)));
      final var last =
          new CompletedChange(
              3,
              ClusterChangePlan.Status.COMPLETED,
              Instant.ofEpochSecond(100),
              Instant.ofEpochSecond(200));
      final var legacy =
          ClusterConfiguration.builder()
              .version(8)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(pending))
              .lastChange(Optional.of(last))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — startedAt of the pending plan and both timestamps of the last change are preserved
      assertThat(migrated.phasedChangeState().onlyPending().startedAt())
          .isEqualTo(pendingStartedAt);
      final var lastChange = migrated.phasedChangeState().lastChange().orElseThrow();
      assertThat(lastChange.startedAt()).isEqualTo(Instant.ofEpochSecond(100));
      assertThat(lastChange.completedAt()).isEqualTo(Instant.ofEpochSecond(200));
    }

    @Test
    void shouldProduceNoPendingPlanWhenLegacyPendingHasNoOperations() {
      // given — a pending change plan present but with no operations
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .pendingChanges(Optional.of(ClusterChangePlan.init(2, List.of())))
              .build();

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — no pending phased plan is produced
      assertThat(migrated.phasedChangeState().pending()).isEmpty();
    }
  }

  @Nested
  class Merge {

    @Test
    void shouldAdoptPartitionGroupPresentOnlyOnOneSide() {
      // given — "a" only on the left, "b" only on the right
      final var left =
          config(global(1, Map.of()), Map.of("a", group(1, Map.of())), PhasedChangeState.empty());
      final var right =
          config(global(1, Map.of()), Map.of("b", group(1, Map.of())), PhasedChangeState.empty());

      // when
      final var merged = left.merge(right);

      // then — union of group keys
      assertThat(merged.partitionGroups()).containsOnlyKeys("a", "b");
    }

    @Test
    void shouldMergeOverlappingPartitionGroup() {
      // given — both have group "a" at the same config version, with different members
      final var left =
          config(
              global(1, Map.of()),
              Map.of("a", group(3, Map.of(MEMBER_0, brokerPartition(1)))),
              PhasedChangeState.empty());
      final var right =
          config(
              global(1, Map.of()),
              Map.of("a", group(3, Map.of(MEMBER_1, brokerPartition(2)))),
              PhasedChangeState.empty());

      // when
      final var merged = left.merge(right);

      // then — the group merge delegates to PartitionGroupConfiguration.merge (member union)
      assertThat(merged.partitionGroup("a").members()).containsOnlyKeys(MEMBER_0, MEMBER_1);
    }

    @Test
    void shouldDelegateGlobalConfigurationMergeToHigherVersion() {
      // given — the right side has a higher global-config version
      final var left = config(global(1, Map.of()), Map.of(), PhasedChangeState.empty());
      final var higherGlobal = global(2, Map.of(MEMBER_0, broker(0, BrokerState.State.ACTIVE)));
      final var right = config(higherGlobal, Map.of(), PhasedChangeState.empty());

      // when
      final var merged = left.merge(right);

      // then
      assertThat(merged.globalConfiguration()).isEqualTo(higherGlobal);
    }

    @Test
    void shouldMergeGlobalMembersFieldByFieldAtEqualVersion() {
      // given — equal global-config version; MEMBER_0 newer on the left, MEMBER_1 only on the right
      final var left =
          config(
              global(3, Map.of(MEMBER_0, broker(9, BrokerState.State.ACTIVE))),
              Map.of(),
              PhasedChangeState.empty());
      final var right =
          config(
              global(
                  3,
                  Map.of(
                      MEMBER_0,
                      broker(2, BrokerState.State.LEAVING),
                      MEMBER_1,
                      broker(1, BrokerState.State.ACTIVE))),
              Map.of(),
              PhasedChangeState.empty());

      // when
      final var merged = left.merge(right);

      // then — higher per-member version wins and the member union is kept
      assertThat(merged.globalConfiguration().members()).containsOnlyKeys(MEMBER_0, MEMBER_1);
      assertThat(merged.globalConfiguration().getMember(MEMBER_0).version()).isEqualTo(9);
    }

    @Test
    void shouldMergePhasedChangeStateLastChangeByHigherId() {
      // given — no pending plan, but different last completed changes
      final var older =
          new PhasedChangeState(
              3L,
              Map.of(),
              List.of(
                  new CompletedPhasedChange(
                      2, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH)));
      final var newer =
          new PhasedChangeState(
              6L,
              Map.of(),
              List.of(
                  new CompletedPhasedChange(
                      5, PhasedChangePlanStatus.FAILED, Instant.EPOCH, Instant.EPOCH)));
      final var left = config(global(1, Map.of()), Map.of(), older);
      final var right = config(global(1, Map.of()), Map.of(), newer);

      // when / then — the higher-id last change wins, both merge directions
      assertThat(left.merge(right).phasedChangeState().lastChange().orElseThrow().id())
          .isEqualTo(5);
      assertThat(right.merge(left).phasedChangeState().lastChange().orElseThrow().id())
          .isEqualTo(5);
    }

    @Test
    void shouldDelegatePhasedChangeStateMerge() {
      // given — same plan id, this at phase 0, other at phase 1
      final List<Phase> phases = List.of(new GlobalPhase(List.of()), new GlobalPhase(List.of()));
      final var atPhase0 = PhasedChangeState.empty().initPlan(phases);
      final var atPhase1 = atPhase0.withAdvancedPlan(atPhase0.onlyPending().withNextPhase());
      final var left = config(global(1, Map.of()), Map.of(), atPhase0);
      final var right = config(global(1, Map.of()), Map.of(), atPhase1);

      // when
      final var merged = left.merge(right);

      // then — higher phase index wins (delegated to PhasedChangeState/PhasedChangePlan merge)
      assertThat(merged.phasedChangeState().onlyPending().currentPhaseIndex()).isEqualTo(1);
    }
  }

  @Nested
  class Updates {

    @Test
    void shouldUpdateGlobalConfiguration() {
      // given
      final var config =
          config(global(4, Map.of()), Map.of("a", group(1, Map.of())), PhasedChangeState.empty());

      // when
      final var updated = config.updateGlobalConfiguration(g -> g.setClusterId("cluster-x"));

      // then
      assertThat(updated.globalConfiguration().clusterId()).contains("cluster-x");
      assertThat(updated.partitionGroups()).isEqualTo(config.partitionGroups());
    }

    @Test
    void shouldReturnSameWhenGlobalConfigurationUnchanged() {
      // given
      final var config = config(global(4, Map.of()), Map.of(), PhasedChangeState.empty());

      // when / then
      assertThat(config.updateGlobalConfiguration(UnaryOperator.identity())).isSameAs(config);
    }

    @Test
    void shouldUpdatePartitionGroupConfig() {
      // given
      final var config =
          config(global(1, Map.of()), Map.of("a", group(4, Map.of())), PhasedChangeState.empty());

      // when — start a change on group "a"
      final var updated =
          config.updatePartitionGroupConfig(
              "a",
              g ->
                  g.startGraphConfigurationChange(
                      OperationGraph.sequential(List.of(new DeleteHistoryOperation(MEMBER_0)))));

      // then
      assertThat(updated.partitionGroup("a").hasPendingChanges()).isTrue();
      assertThat(updated.partitionGroup("a").version()).isEqualTo(5);
    }

    @Test
    void shouldThrowWhenUpdatingUnknownPartitionGroup() {
      // given
      final var config = config(global(1, Map.of()), Map.of(), PhasedChangeState.empty());

      // when / then
      assertThatThrownBy(
              () -> config.updatePartitionGroupConfig("missing", UnaryOperator.identity()))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class ActivePartitionGroups {

    @Test
    void shouldExcludeADisabledGroup() {
      // given — "a" is enabled, "b" is disabled
      final var config =
          config(
              global(1, Map.of()),
              Map.of("a", group(1, Map.of()), "b", group(1, Map.of()).disable()),
              PhasedChangeState.empty());

      // when / then
      assertThat(config.activePartitionGroups()).containsOnlyKeys("a");
    }

    @Test
    void shouldReturnEveryGroupWhenNoneIsDisabled() {
      // given
      final var config =
          config(
              global(1, Map.of()),
              Map.of("a", group(1, Map.of()), "b", group(1, Map.of())),
              PhasedChangeState.empty());

      // when / then
      assertThat(config.activePartitionGroups()).containsOnlyKeys("a", "b");
    }

    @Test
    void shouldReturnEmptyWhenEveryGroupIsDisabled() {
      // given
      final var config =
          config(
              global(1, Map.of()),
              Map.of("a", group(1, Map.of()).disable()),
              PhasedChangeState.empty());

      // when / then
      assertThat(config.activePartitionGroups()).isEmpty();
    }
  }

  @Nested
  class WithoutDisabledPartitionGroups {

    @Test
    void shouldExcludeADisabledGroupButKeepOtherFields() {
      // given — "a" is enabled, "b" is disabled
      final var config =
          config(
              global(1, Map.of()),
              Map.of("a", group(1, Map.of()), "b", group(1, Map.of()).disable()),
              PhasedChangeState.empty());

      // when
      final var view = config.withoutDisabledPartitionGroups();

      // then
      assertThat(view.partitionGroups()).containsOnlyKeys("a");
      assertThat(view.version()).isEqualTo(config.version());
      assertThat(view.globalConfiguration()).isEqualTo(config.globalConfiguration());
      assertThat(view.phasedChangeState()).isEqualTo(config.phasedChangeState());
    }

    @Test
    void shouldReturnEveryGroupWhenNoneIsDisabled() {
      // given
      final var config =
          config(
              global(1, Map.of()),
              Map.of("a", group(1, Map.of()), "b", group(1, Map.of())),
              PhasedChangeState.empty());

      // when / then
      assertThat(config.withoutDisabledPartitionGroups().partitionGroups())
          .containsOnlyKeys("a", "b");
    }
  }

  @Nested
  class PlanTransitions {

    private static GlobalPhase globalPhase() {
      return new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0)));
    }

    private static PartitionGroupPhase groupPhase(final String groupId) {
      return PartitionGroupPhase.sequential(
          Map.of(groupId, List.of(new DeleteHistoryOperation(MEMBER_0))));
    }

    @Test
    void shouldInitPlanAndActivateFirstPhase() {
      // given — an empty config
      final var config = CurrentClusterConfiguration.init();

      // when — init a plan whose first phase is a global phase
      final var updated = config.initPlan(List.of(globalPhase()));

      // then — the plan is pending at phase 0 (id derived by PhasedChangeState) and activated
      assertThat(updated.phasedChangeState().pending()).isNotEmpty();
      assertThat(updated.phasedChangeState().onlyPending().currentPhaseIndex()).isZero();
      assertThat(updated.phasedChangeState().onlyPending().id()).isEqualTo(1);
      assertThat(updated.globalConfiguration().hasPendingChanges()).isTrue();
    }

    @Test
    void shouldThrowWhenInitPlanWhileOneIsPending() {
      // given
      final var config = CurrentClusterConfiguration.init().initPlan(List.of(globalPhase()));

      // when / then
      assertThatThrownBy(() -> config.initPlan(List.of(globalPhase())))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldActivateNextPhase() {
      // given — plan: phase 0 global, phase 1 targets group "a"
      final var config =
          config(global(1, Map.of()), Map.of("a", group(1, Map.of())), PhasedChangeState.empty())
              .initPlan(List.of(globalPhase(), groupPhase("a")));

      // when
      final var updated = config.activateNextPhase(config.phasedChangeState().onlyPending().id());

      // then — now on phase 1, and group "a" has the activated change
      assertThat(updated.phasedChangeState().onlyPending().currentPhaseIndex()).isEqualTo(1);
      assertThat(updated.partitionGroup("a").hasPendingChanges()).isTrue();
    }

    @Test
    void shouldActivateNextPhaseWhenExpectedPhaseIndexMatchesCurrent() {
      // given — plan: phase 0 global, phase 1 targets group "a"
      final var config =
          config(global(1, Map.of()), Map.of("a", group(1, Map.of())), PhasedChangeState.empty())
              .initPlan(List.of(globalPhase(), groupPhase("a")));
      final var planId = config.phasedChangeState().onlyPending().id();

      // when — expectedPhaseIndex (0) matches the plan's actual current phase index
      final var updated = config.tryActivateNextPhase(planId, 0);

      // then — advances exactly as the unguarded overload would
      assertThat(updated.phasedChangeState().onlyPending().currentPhaseIndex()).isEqualTo(1);
      assertThat(updated.partitionGroup("a").hasPendingChanges()).isTrue();
    }

    @Test
    void shouldNoOpActivateNextPhaseWhenExpectedPhaseIndexIsStale() {
      // given — a plan already advanced to phase 1 by an earlier trigger
      final var config =
          config(global(1, Map.of()), Map.of("a", group(1, Map.of())), PhasedChangeState.empty())
              .initPlan(List.of(globalPhase(), groupPhase("a")));
      final var planId = config.phasedChangeState().onlyPending().id();
      final var advanced = config.activateNextPhase(planId);

      // when — a second, stale trigger still expects phase 0 (the phase it observed as complete
      // before the first trigger's advance ran)
      final var result = advanced.tryActivateNextPhase(planId, 0);

      // then — no-op: the stale re-validation is rejected instead of throwing or double-advancing
      assertThat(result).isEqualTo(advanced);
    }

    @Test
    void shouldNoOpActivateNextPhaseWithExpectedPhaseIndexWhenPlanNoLongerPending() {
      // given — a plan that has already completed
      final var config = CurrentClusterConfiguration.init().initPlan(List.of(globalPhase()));
      final var planId = config.phasedChangeState().onlyPending().id();
      final var completed = config.completePlan(planId, PhasedChangePlanStatus.COMPLETED);

      // when — a stale trigger tries to advance the now-resolved plan
      final var result = completed.tryActivateNextPhase(planId, 0);

      // then — no-op, not an exception
      assertThat(result).isEqualTo(completed);
    }

    @Test
    void shouldThrowWhenActivatingNextPhaseOnLastPhase() {
      // given — a single-phase plan
      final var config = CurrentClusterConfiguration.init().initPlan(List.of(globalPhase()));
      final var planId = config.phasedChangeState().onlyPending().id();

      // when / then
      assertThatThrownBy(() -> config.activateNextPhase(planId))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenActivatingNextPhaseWithNoPendingPlan() {
      // given
      final var config = CurrentClusterConfiguration.init();

      // when / then
      assertThatThrownBy(() -> config.activateNextPhase(1L))
          .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(PhasedChangePlanStatus.class)
    void shouldCompletePlanWithTerminalStatus(final PhasedChangePlanStatus status) {
      // given
      final var config = CurrentClusterConfiguration.init().initPlan(List.of(globalPhase()));

      // when
      final var completed =
          config.completePlan(config.phasedChangeState().onlyPending().id(), status);

      // then — the pending plan is moved into the last completed change with the given status
      assertThat(completed.phasedChangeState().pending()).isEmpty();
      assertThat(completed.phasedChangeState().lastChange()).isPresent();
      assertThat(completed.phasedChangeState().lastChange().orElseThrow().status())
          .isEqualTo(status);
    }

    @Test
    void shouldCompletePlanWhenExpectedPhaseIndexMatchesCurrent() {
      // given
      final var config = CurrentClusterConfiguration.init().initPlan(List.of(globalPhase()));
      final var planId = config.phasedChangeState().onlyPending().id();

      // when
      final var completed = config.tryCompletePlan(planId, 0, PhasedChangePlanStatus.COMPLETED, 10);

      // then — completes exactly as the unguarded overload would
      assertThat(completed.phasedChangeState().pending()).isEmpty();
      assertThat(completed.phasedChangeState().lastChange()).isPresent();
    }

    @Test
    void shouldNoOpCompletePlanWhenExpectedPhaseIndexIsStale() {
      // given — a two-phase plan already advanced to phase 1 by an earlier trigger
      final var config =
          config(global(1, Map.of()), Map.of("a", group(1, Map.of())), PhasedChangeState.empty())
              .initPlan(List.of(globalPhase(), groupPhase("a")));
      final var planId = config.phasedChangeState().onlyPending().id();
      final var advanced = config.activateNextPhase(planId);

      // when — a stale trigger tries to complete the plan as if it were still on phase 0
      final var result = advanced.tryCompletePlan(planId, 0, PhasedChangePlanStatus.COMPLETED, 10);

      // then — no-op: the plan is still pending at phase 1, untouched
      assertThat(result).isEqualTo(advanced);
    }

    @Test
    void shouldNoOpCompletePlanWithExpectedPhaseIndexWhenPlanNoLongerPending() {
      // given — a plan that has already completed
      final var config = CurrentClusterConfiguration.init().initPlan(List.of(globalPhase()));
      final var planId = config.phasedChangeState().onlyPending().id();
      final var completed = config.completePlan(planId, PhasedChangePlanStatus.COMPLETED);

      // when — a stale duplicate completion trigger for the same, already-resolved plan
      final var result = completed.tryCompletePlan(planId, 0, PhasedChangePlanStatus.CANCELLED, 10);

      // then — no-op, not an exception; the plan's original terminal status is untouched
      assertThat(result).isEqualTo(completed);
    }

    @Test
    void shouldThrowWhenInitPlanWithNoPhases() {
      // given
      final var config = CurrentClusterConfiguration.init();

      // when / then
      assertThatThrownBy(() -> config.initPlan(List.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenCompletingWithNoPendingPlan() {
      // given
      final var config = CurrentClusterConfiguration.init();

      // when / then
      assertThatThrownBy(() -> config.completePlan(1L, PhasedChangePlanStatus.COMPLETED))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldActivateOnlyGlobalConfigurationForGlobalPhase() {
      // given — a config with a partition group and a global-phase plan
      final var config =
          config(global(1, Map.of()), Map.of("a", group(1, Map.of())), PhasedChangeState.empty());

      // when
      final var updated = config.initPlan(List.of(globalPhase()));

      // then — only the global config is changed; the partition group is untouched
      assertThat(updated.globalConfiguration().hasPendingChanges()).isTrue();
      assertThat(updated.partitionGroup("a").hasPendingChanges()).isFalse();
    }

    @Test
    void shouldActivateOnlyNamedGroupsForParallelPhase() {
      // given — a config with groups "a" and "b" and a parallel phase targeting only "a"
      final var config =
          config(
              global(1, Map.of()),
              Map.of("a", group(1, Map.of()), "b", group(1, Map.of())),
              PhasedChangeState.empty());

      // when
      final var updated = config.initPlan(List.of(groupPhase("a")));

      // then — only group "a" is changed; group "b" and the global config are untouched
      assertThat(updated.partitionGroup("a").hasPendingChanges()).isTrue();
      assertThat(updated.partitionGroup("b").hasPendingChanges()).isFalse();
      assertThat(updated.globalConfiguration().hasPendingChanges()).isFalse();
    }
  }

  @Nested
  class IsCurrentPhaseComplete {

    @Test
    void shouldThrowWhenNoPlanIsPending() {
      // given
      final var config = CurrentClusterConfiguration.init();

      // when / then
      assertThatThrownBy(() -> config.isCurrentPhaseComplete(1L))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldBeIncompleteForGlobalPhaseWithPendingChanges() {
      // given — a plan whose current phase is global, freshly activated
      final var config =
          CurrentClusterConfiguration.init()
              .initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0)))));
      final var planId = config.phasedChangeState().onlyPending().id();

      // then — the global configuration still has the phase's operation pending
      assertThat(config.isCurrentPhaseComplete(planId)).isFalse();
    }

    @Test
    void shouldBeCompleteForGlobalPhaseOnceDrained() {
      // given — the global phase's own operation has been applied and advanced
      final var config =
          CurrentClusterConfiguration.init()
              .initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0)))));
      final var planId = config.phasedChangeState().onlyPending().id();
      final var drained = drainGlobal(config);

      // then
      assertThat(drained.isCurrentPhaseComplete(planId)).isTrue();
    }

    @Test
    void shouldBeIncompleteForGroupPhaseUntilEveryNamedGroupDrains() {
      // given — a phase targeting groups "a" and "b"; only "a" has drained
      final var phase =
          PartitionGroupPhase.sequential(
              Map.of(
                  "a", List.of(new DeleteHistoryOperation(MEMBER_0)),
                  "b", List.of(new DeleteHistoryOperation(MEMBER_0))));
      final var config =
          config(
                  global(1, Map.of()),
                  Map.of("a", group(1, Map.of()), "b", group(1, Map.of())),
                  PhasedChangeState.empty())
              .initPlan(List.of(phase));
      final var planId = config.phasedChangeState().onlyPending().id();

      // when — group "a" drains its side of the phase, "b" is still pending
      final var partiallyDrained = drainGroup(config, "a");

      // then
      assertThat(partiallyDrained.isCurrentPhaseComplete(planId)).isFalse();

      // when — group "b" drains too
      final var fullyDrained = drainGroup(partiallyDrained, "b");

      // then
      assertThat(fullyDrained.isCurrentPhaseComplete(planId)).isTrue();
    }
  }

  @Nested
  class CancelPendingChanges {

    @Test
    void shouldReturnSameConfigWhenNoPlanIsPending() {
      // given
      final var config = CurrentClusterConfiguration.init();

      // when
      final var cancelled = config.cancelPendingChanges(1L);

      // then
      assertThat(cancelled).isEqualTo(config);
    }

    @Test
    void shouldClearPendingChangesOnGlobalConfigurationForGlobalPhase() {
      // given
      final var config =
          CurrentClusterConfiguration.init()
              .initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0)))));
      final var changeId = config.phasedChangeState().onlyPending().id();

      // when
      final var cancelled = config.cancelPendingChanges(changeId);

      // then
      assertThat(cancelled.globalConfiguration().hasPendingChanges()).isFalse();
      assertThat(cancelled.phasedChangeState().pending()).isEmpty();
      assertThat(cancelled.phasedChangeState().lastChange()).isPresent();
      assertThat(cancelled.phasedChangeState().lastChange().orElseThrow().id()).isEqualTo(changeId);
      assertThat(cancelled.phasedChangeState().lastChange().orElseThrow().status())
          .isEqualTo(PhasedChangePlanStatus.CANCELLED);
    }

    @Test
    void shouldClearPendingChangesOnEveryTargetedPartitionGroup() {
      // given — a plan targeting groups "a" and "b" in a single parallel phase
      final var config =
          config(
                  global(1, Map.of()),
                  Map.of("a", group(1, Map.of()), "b", group(1, Map.of())),
                  PhasedChangeState.empty())
              .initPlan(
                  List.of(
                      PartitionGroupPhase.sequential(
                          Map.of(
                              "a",
                              List.of(new DeleteHistoryOperation(MEMBER_0)),
                              "b",
                              List.of(new DeleteHistoryOperation(MEMBER_0))))));
      final var changeId = config.phasedChangeState().onlyPending().id();

      // when
      final var cancelled = config.cancelPendingChanges(changeId);

      // then
      assertThat(cancelled.partitionGroup("a").hasPendingChanges()).isFalse();
      assertThat(cancelled.partitionGroup("b").hasPendingChanges()).isFalse();
      assertThat(cancelled.phasedChangeState().pending()).isEmpty();
      assertThat(cancelled.phasedChangeState().lastChange().orElseThrow().status())
          .isEqualTo(PhasedChangePlanStatus.CANCELLED);
    }

    @Test
    void shouldNotClearPendingChangesOnGroupNotYetTargetedByCurrentPhase() {
      // given — a two-phase plan: phase 0 targets group "a", phase 1 (not yet activated) targets
      // "b"
      final var config =
          config(
                  global(1, Map.of()),
                  Map.of("a", group(1, Map.of()), "b", group(1, Map.of())),
                  PhasedChangeState.empty())
              .initPlan(
                  List.of(
                      PartitionGroupPhase.sequential(
                          Map.of("a", List.of(new DeleteHistoryOperation(MEMBER_0)))),
                      PartitionGroupPhase.sequential(
                          Map.of("b", List.of(new DeleteHistoryOperation(MEMBER_0))))));
      final var changeId = config.phasedChangeState().onlyPending().id();

      // when — cancel while phase 0 is still active
      final var cancelled = config.cancelPendingChanges(changeId);

      // then — only the already-activated group "a" is cleared; "b" is untouched, still at its
      // original version since it was never activated
      assertThat(cancelled.partitionGroup("a").hasPendingChanges()).isFalse();
      assertThat(cancelled.partitionGroup("b")).isEqualTo(group(1, Map.of()));
      assertThat(cancelled.phasedChangeState().pending()).isEmpty();
    }
  }

  @Nested
  class ToLegacyDefault {

    @Test
    void shouldRoundTripMembersAndClusterFields() {
      // given — an active member with a partition and a member that has left with no partitions
      final var active =
          new MemberState(4, Instant.EPOCH, MemberState.State.ACTIVE, Map.of(1, partition()));
      final var left = new MemberState(2, Instant.EPOCH, MemberState.State.LEFT, Map.of());
      final var legacy =
          ClusterConfiguration.builder()
              .version(7)
              .members(Map.of(MEMBER_0, active, MEMBER_1, left))
              .clusterId(Optional.of("cluster-x"))
              .partitionDistributorConfig(Optional.of(new RoundRobinConfig()))
              .incarnationNumber(3)
              .build();

      // when — migrate to the new model and project back
      final var projected = CurrentClusterConfiguration.fromLegacy(legacy).toLegacyDefault();

      // then — the projection reproduces the original legacy configuration
      assertThat(projected.members()).isEqualTo(legacy.members());
      assertThat(projected.clusterId()).contains("cluster-x");
      assertThat(projected.partitionDistributorConfig()).contains(new RoundRobinConfig());
      assertThat(projected.incarnationNumber()).isEqualTo(3);
      assertThat(projected.version()).isEqualTo(7);
    }

    @Test
    void shouldProjectRecoveringModeToRecoveringState() {
      // given
      final var recovering =
          new MemberState(5, Instant.EPOCH, MemberState.State.RECOVERING, Map.of(1, partition()));
      final var legacy =
          ClusterConfiguration.builder().version(1).members(Map.of(MEMBER_0, recovering)).build();

      // when
      final var projected = CurrentClusterConfiguration.fromLegacy(legacy).toLegacyDefault();

      // then — the recovering member is projected back to the legacy RECOVERING state
      assertThat(projected.getMember(MEMBER_0).state()).isEqualTo(MemberState.State.RECOVERING);
      assertThat(projected.getMember(MEMBER_0).partitions()).containsOnlyKeys(1);
    }

    @Test
    void shouldProjectLastCompletedChange() {
      // given
      final var last =
          new CompletedChange(3, ClusterChangePlan.Status.FAILED, Instant.EPOCH, Instant.EPOCH);
      final var legacy =
          ClusterConfiguration.builder()
              .version(5)
              .members(Map.of(MEMBER_0, activeMember()))
              .lastChange(Optional.of(last))
              .build();

      // when
      final var projected = CurrentClusterConfiguration.fromLegacy(legacy).toLegacyDefault();

      // then
      assertThat(projected.lastChange()).contains(last);
    }

    @Test
    void shouldProjectEmptyDefaultGroupWhenAbsent() {
      // given — a wrapper with only a global configuration and no partition groups
      final var config = CurrentClusterConfiguration.init();

      // when / then — projecting does not fail and yields an empty legacy configuration
      final var projected = config.toLegacyDefault();
      assertThat(projected.members()).isEmpty();
      assertThat(projected.hasPendingChanges()).isFalse();
    }

    @Test
    void shouldProjectAGroupsChangeAsTheGraphItIsRunning() {
      // given — the default group is running a change whose two operations have no edge between
      // them, so they may run at the same time
      final var graph = OperationGraph.builder();
      graph.add(new PartitionJoinOperation(MEMBER_0, 1, 1, true));
      graph.add(new PartitionJoinOperation(MEMBER_1, 1, 1, true));
      final var groupId = CurrentClusterConfiguration.DEFAULT_GROUP;
      final var config =
          config(
                  global(1, Map.of(MEMBER_0, broker(1, BrokerState.State.ACTIVE))),
                  Map.of(groupId, group(1, Map.of(MEMBER_0, brokerPartition(1)))),
                  PhasedChangeState.empty())
              .initPlan(List.of(new PartitionGroupPhase(Map.of(groupId, graph.build()))));

      // when
      final var projected = config.toLegacyDefault();

      // then — the projection carries the group's plan itself, not a queue standing in for it: a
      // consumer reading it in-process can still see that both operations are runnable now
      assertThat(projected.pendingChanges())
          .contains(config.partitionGroup(groupId).pendingChanges().orElseThrow());
    }

    @Test
    void shouldProjectTheGlobalConfigurationsChangeAsTheModelItIsRunning() {
      // given — the global configuration is running a change
      final var config =
          config(
                  global(1, Map.of(MEMBER_0, broker(1, BrokerState.State.ACTIVE))),
                  Map.of(),
                  PhasedChangeState.empty())
              .initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_1)))));

      // when
      final var projected = config.toLegacyDefault();

      // then
      assertThat(projected.pendingChanges())
          .contains(config.globalConfiguration().pendingChanges().orElseThrow());
    }

    @Test
    void shouldProjectUninitializedAsUninitialized() {
      // given — a wrapper that has never been initialized (distinct from
      // CurrentClusterConfiguration.init())
      final var config = CurrentClusterConfiguration.uninitialized();

      // when
      final var projected = config.toLegacyDefault();

      // then — the projection must report uninitialized too; deriving the legacy version from the
      // sub-configs' own (0) uninitialized sentinel could never equal the legacy sentinel (-1),
      // so this must be handled explicitly rather than falling through to the generic projection
      assertThat(projected.isUninitialized()).isTrue();
    }
  }

  @Nested
  class Validation {

    @Test
    void shouldThrowWhenGlobalConfigurationIsNull() {
      assertThatThrownBy(
              () -> new CurrentClusterConfiguration(0, null, Map.of(), PhasedChangeState.empty()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenPartitionGroupsIsNull() {
      assertThatThrownBy(
              () ->
                  new CurrentClusterConfiguration(
                      0, GlobalConfiguration.init(), null, PhasedChangeState.empty()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenPhasedChangeStateIsNull() {
      assertThatThrownBy(
              () -> new CurrentClusterConfiguration(0, GlobalConfiguration.init(), Map.of(), null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class DesiredLeaders {

    @Test
    void shouldExposeDesiredLeaderPerGroupAndPartition() {
      // given
      final var groupA =
          group(
              1,
              Map.of(
                  MEMBER_0, brokerPartition(1, 1),
                  MEMBER_1, brokerPartition(1, 3)));
      final var groupB =
          group(
              1,
              Map.of(
                  MEMBER_0, brokerPartition(1, 5),
                  MEMBER_1, brokerPartition(1, 2)));
      final var config =
          config(
              global(1, Map.of()),
              Map.of("group-a", groupA, "group-b", groupB),
              PhasedChangeState.empty());

      // when / then
      final var desiredLeaders = config.desiredLeaders();
      assertThat(desiredLeaders).containsOnlyKeys("group-a", "group-b");
      assertThat(desiredLeaders.get("group-a")).containsExactly(Map.entry(1, MEMBER_1));
      assertThat(desiredLeaders.get("group-b")).containsExactly(Map.entry(1, MEMBER_0));
    }

    @Test
    void shouldReturnEmptyMapWhenNoGroups() {
      // given
      final var config = config(global(1, Map.of()), Map.of(), PhasedChangeState.empty());

      // when / then
      assertThat(config.desiredLeaders()).isEmpty();
    }
  }
}
