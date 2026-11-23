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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  class ActivatePendingPhase {

    @Test
    void shouldActivateThePendingPlansCurrentPhase() {
      // given - a plan that is pending but whose first phase has not been activated, as a restore
      // leaves it
      final var phase =
          PartitionGroupPhase.sequential(
              Map.of(
                  CurrentClusterConfiguration.DEFAULT_GROUP,
                  List.of(new DeleteHistoryOperation(MEMBER_0))));
      final var config =
          new CurrentClusterConfiguration(
              CurrentClusterConfiguration.INITIAL_VERSION,
              global(1, Map.of(MEMBER_0, broker(1, BrokerState.State.ACTIVE))),
              Map.of(
                  CurrentClusterConfiguration.DEFAULT_GROUP,
                  group(1, Map.of(MEMBER_0, brokerPartition(1)))),
              PhasedChangeState.empty().initPlan(List.of(phase)));

      // when
      final var activated = config.activatePendingPhase();

      // then
      assertThat(
              activated.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).pendingChanges())
          .hasValueSatisfying(
              plan ->
                  assertThat(plan.pendingOperations())
                      .containsExactly(new DeleteHistoryOperation(MEMBER_0)));
      assertThat(activated.phasedChangeState()).isEqualTo(config.phasedChangeState());
    }

    @Test
    void shouldReturnTheSameConfigurationWhenNoPlanIsPending() {
      // given
      final var config = CurrentClusterConfiguration.init();

      // when
      final var activated = config.activatePendingPhase();

      // then
      assertThat(activated).isEqualTo(config);
    }
  }

  @Nested
  class IsAfterRestore {

    @Test
    void shouldDetectRestoreOfASinglePhysicalTenant() {
      // given
      final var configuration = restoreConfiguration("default");

      // when / then
      assertThat(configuration.isAfterRestore()).isTrue();
    }

    @Test
    void shouldDetectRestoreOfSeveralPhysicalTenants() {
      // given — what RestoreManager writes for a multi-tenant cluster: one phase naming every
      // restored group, each running its own UpdateRoutingState
      final var configuration = restoreConfiguration("default", "tenant-a", "tenant-b");

      // when / then — the post-restore handling must not switch itself off as tenants are added
      assertThat(configuration.isAfterRestore()).isTrue();
    }

    @Test
    void shouldNotDetectRestoreForAPhaseNamingNoGroup() {
      // given — a restore plan whose phase targets nothing; there is no group to reconcile on
      // anyone's behalf, so this must not pass as a restore just because every (absent) group
      // satisfies the per-group shape check vacuously
      final var plan =
          PhasedChangePlan.initForRestore(
              List.of(new PartitionGroupPhase(Map.of())), Instant.EPOCH);
      final var configuration =
          config(
              global(1, Map.of()),
              Map.of("default", group(1, Map.of())),
              new PhasedChangeState(
                  PhasedChangePlan.INITIAL_PLAN_ID, Map.of(plan.id(), plan), List.of()));

      // when / then
      assertThat(configuration.isAfterRestore()).isFalse();
    }

    @Test
    void shouldNotDetectRestoreWhenAGroupRunsMoreThanUpdateRoutingState() {
      // given — one group carries an extra operation, so this is not the shape a restore produces
      final var plan =
          PhasedChangePlan.initForRestore(
              List.of(
                  PartitionGroupPhase.sequential(
                      Map.of(
                          "default",
                          List.of(updateRoutingState()),
                          "tenant-a",
                          List.of(updateRoutingState(), new DeleteHistoryOperation(MEMBER_0))))),
              Instant.EPOCH);
      final var configuration =
          config(
              global(1, Map.of()),
              Map.of("default", group(1, Map.of()), "tenant-a", group(1, Map.of())),
              new PhasedChangeState(
                  PhasedChangePlan.INITIAL_PLAN_ID, Map.of(plan.id(), plan), List.of()));

      // when / then
      assertThat(configuration.isAfterRestore()).isFalse();
    }

    @Test
    void shouldNotDetectRestoreForAnOrdinaryMultiGroupUpdateRoutingState() {
      // given — the same per-group shape as a restore, but issued by the plan counter rather than
      // carrying the restore sentinel id: an admin-triggered updateRoutingState across tenants
      final var phase =
          PartitionGroupPhase.sequential(
              Map.of(
                  "default", List.of(updateRoutingState()),
                  "tenant-a", List.of(updateRoutingState())));
      final var plan =
          PhasedChangePlan.init(PhasedChangePlan.INITIAL_PLAN_ID, List.of(phase), Instant.EPOCH);
      final var configuration =
          config(
              global(1, Map.of()),
              Map.of("default", group(1, Map.of()), "tenant-a", group(1, Map.of())),
              new PhasedChangeState(
                  PhasedChangePlan.INITIAL_PLAN_ID + 1, Map.of(plan.id(), plan), List.of()));

      // when / then
      assertThat(configuration.isAfterRestore()).isFalse();
    }

    private static CurrentClusterConfiguration restoreConfiguration(final String... groupIds) {
      final Map<String, List<PartitionGroupOperation>> operations = new TreeMap<>();
      final Map<String, PartitionGroupConfiguration> groups = new TreeMap<>();
      for (final var groupId : groupIds) {
        operations.put(groupId, List.of(updateRoutingState()));
        groups.put(groupId, group(1, Map.of()));
      }
      final var plan =
          PhasedChangePlan.initForRestore(
              List.of(PartitionGroupPhase.sequential(operations)), Instant.EPOCH);
      return config(
          global(1, Map.of()),
          groups,
          new PhasedChangeState(
              PhasedChangePlan.INITIAL_PLAN_ID, Map.of(plan.id(), plan), List.of()));
    }

    private static PartitionGroupOperation updateRoutingState() {
      return new PartitionGroupOperation.UpdateRoutingState(MEMBER_0, Optional.empty());
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
  class GroupView {

    private static final String GROUP = "tenant";

    private static CurrentClusterConfiguration config(
        final long globalVersion, final long groupVersion) {
      return new CurrentClusterConfiguration(
          CurrentClusterConfiguration.INITIAL_VERSION,
          global(globalVersion, Map.of(MEMBER_0, broker(1, BrokerState.State.ACTIVE))),
          Map.of(GROUP, group(groupVersion, Map.of(MEMBER_0, brokerPartition(1)))),
          PhasedChangeState.empty());
    }

    @Test
    void shouldReportTheHigherOfGlobalAndGroupVersion() {
      // when - then
      assertThat(config(3, 7).versionOf(GROUP)).isEqualTo(7);
      assertThat(config(9, 7).versionOf(GROUP)).isEqualTo(9);
    }

    @Test
    void shouldReportTheGlobalVersionForAnUnknownGroup() {
      // when - then
      assertThat(config(3, 7).versionOf("unknown")).isEqualTo(3);
    }

    @Test
    void shouldReportNoPendingChangesWhenNothingRuns() {
      // when - then
      assertThat(config(1, 1).pendingChangesOf(GROUP)).isEmpty();
    }

    @Test
    void shouldReportTheGroupsPendingChanges() {
      // given
      final var config =
          config(1, 1)
              .initPlan(
                  List.of(
                      PartitionGroupPhase.sequential(
                          Map.of(GROUP, List.of(new DeleteHistoryOperation(MEMBER_0))))));

      // when - then
      assertThat(config.pendingChangesOf(GROUP))
          .isEqualTo(config.partitionGroup(GROUP).pendingChanges());
      assertThat(config.pendingChangesOf("unknown")).isEmpty();
    }

    @Test
    void shouldPreferTheGlobalPendingChanges() {
      // given
      final var config =
          config(1, 1)
              .initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0)))));

      // when - then
      assertThat(config.pendingChangesOf(GROUP))
          .isPresent()
          .isEqualTo(config.globalConfiguration().pendingChanges());
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
