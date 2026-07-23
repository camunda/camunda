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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
      final var partitionJoin = new PartitionJoinOperation(MEMBER_0, 1, 1);
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

      // when
      final var migrated = CurrentClusterConfiguration.fromLegacy(legacy);

      // then — the ops become three phases in order: [global], [default: 2 partition ops], [global]
      final var plan = migrated.phasedChangeState().pending().orElseThrow();
      assertThat(plan.id()).isEqualTo(9);
      assertThat(plan.currentPhaseIndex()).isZero();
      assertThat(plan.phases())
          .containsExactly(
              new GlobalPhase(List.of(memberJoin)),
              new PartitionGroupParallelPhase(
                  Map.of(
                      CurrentClusterConfiguration.DEFAULT_GROUP,
                      List.of(partitionJoin, partitionLeave))),
              new GlobalPhase(List.of(memberLeave)));
      // and — the pending change is NOT left on the default group
      assertThat(
              migrated.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).pendingChanges())
          .isEmpty();
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
    void shouldNormalizeRestorePendingPlanId() {
      // given — a legacy restore plan uses the negative sentinel id (RESTORE_CHANGE_ID = -2)
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

      // then — the pending plan id is normalized to a positive value
      final var plan = migrated.phasedChangeState().pending().orElseThrow();
      assertThat(plan.id()).isEqualTo(1);
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
      assertThat(withPlan.phasedChangeState().pending().orElseThrow().id()).isEqualTo(1);
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
      assertThat(migrated.phasedChangeState().pending().orElseThrow().phases())
          .containsExactly(new GlobalPhase(List.of(join, leave)));
    }

    @Test
    void shouldGroupConsecutivePartitionOpsIntoSinglePhase() {
      // given — a pending plan with only partition operations
      final var join = new PartitionJoinOperation(MEMBER_0, 1, 1);
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
      assertThat(migrated.phasedChangeState().pending().orElseThrow().phases())
          .containsExactly(
              new PartitionGroupParallelPhase(
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
      assertThat(migrated.phasedChangeState().pending().orElseThrow().startedAt())
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
              Optional.empty(),
              Optional.of(
                  new CompletedPhasedChange(
                      2, PhasedChangePlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH)));
      final var newer =
          new PhasedChangeState(
              Optional.empty(),
              Optional.of(
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
      final var atPhase1 =
          new PhasedChangeState(
              Optional.of(atPhase0.pending().orElseThrow().withNextPhase()), Optional.empty());
      final var left = config(global(1, Map.of()), Map.of(), atPhase0);
      final var right = config(global(1, Map.of()), Map.of(), atPhase1);

      // when
      final var merged = left.merge(right);

      // then — higher phase index wins (delegated to PhasedChangeState/PhasedChangePlan merge)
      assertThat(merged.phasedChangeState().pending().orElseThrow().currentPhaseIndex())
          .isEqualTo(1);
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
              "a", g -> g.startConfigurationChange(List.of(new DeleteHistoryOperation(MEMBER_0))));

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
  class PlanTransitions {

    private static GlobalPhase globalPhase() {
      return new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0)));
    }

    private static PartitionGroupParallelPhase parallelPhase(final String groupId) {
      return new PartitionGroupParallelPhase(
          Map.of(groupId, List.of(new DeleteHistoryOperation(MEMBER_0))));
    }

    @Test
    void shouldInitPlanAndActivateFirstPhase() {
      // given — an empty config
      final var config = CurrentClusterConfiguration.init();

      // when — init a plan whose first phase is a global phase
      final var updated = config.initPlan(List.of(globalPhase()));

      // then — the plan is pending at phase 0 (id derived by PhasedChangeState) and activated
      assertThat(updated.phasedChangeState().pending()).isPresent();
      assertThat(updated.phasedChangeState().pending().orElseThrow().currentPhaseIndex()).isZero();
      assertThat(updated.phasedChangeState().pending().orElseThrow().id()).isEqualTo(1);
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
              .initPlan(List.of(globalPhase(), parallelPhase("a")));

      // when
      final var updated = config.activateNextPhase();

      // then — now on phase 1, and group "a" has the activated change
      assertThat(updated.phasedChangeState().pending().orElseThrow().currentPhaseIndex())
          .isEqualTo(1);
      assertThat(updated.partitionGroup("a").hasPendingChanges()).isTrue();
    }

    @Test
    void shouldThrowWhenActivatingNextPhaseOnLastPhase() {
      // given — a single-phase plan
      final var config = CurrentClusterConfiguration.init().initPlan(List.of(globalPhase()));

      // when / then
      assertThatThrownBy(config::activateNextPhase).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenActivatingNextPhaseWithNoPendingPlan() {
      // given
      final var config = CurrentClusterConfiguration.init();

      // when / then
      assertThatThrownBy(config::activateNextPhase).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(PhasedChangePlanStatus.class)
    void shouldCompletePlanWithTerminalStatus(final PhasedChangePlanStatus status) {
      // given
      final var config = CurrentClusterConfiguration.init().initPlan(List.of(globalPhase()));

      // when
      final var completed = config.completePlan(status);

      // then — the pending plan is moved into the last completed change with the given status
      assertThat(completed.phasedChangeState().pending()).isEmpty();
      assertThat(completed.phasedChangeState().lastChange()).isPresent();
      assertThat(completed.phasedChangeState().lastChange().orElseThrow().status())
          .isEqualTo(status);
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
      assertThatThrownBy(() -> config.completePlan(PhasedChangePlanStatus.COMPLETED))
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
      final var updated = config.initPlan(List.of(parallelPhase("a")));

      // then — only group "a" is changed; group "b" and the global config are untouched
      assertThat(updated.partitionGroup("a").hasPendingChanges()).isTrue();
      assertThat(updated.partitionGroup("b").hasPendingChanges()).isFalse();
      assertThat(updated.globalConfiguration().hasPendingChanges()).isFalse();
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
}
