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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
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
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
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
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests of the new multi-partition-group path through the real {@link
 * ConfigurationChangeCoordinatorImpl} + {@link ClusterConfigurationManagerImpl}, exercising plan
 * generation, phased dispatch and phase advancement for the default tenant.
 */
final class NewModelConfigurationChangeCoordinatorTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @TempDir private Path tmp;

  private ClusterConfigurationManagerImpl manager;
  private ConfigurationChangeCoordinatorImpl coordinator;

  private void wire(final MemberId localMemberId, final CurrentClusterConfiguration seed) {
    final var persisted =
        PersistedCurrentClusterConfiguration.ofFile(
            tmp.resolve("config-" + localMemberId.id() + ".meta"), new ProtoBufSerializer());
    manager =
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
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));
    coordinator = new ConfigurationChangeCoordinatorImpl(manager, localMemberId, executor);
    manager.updateMultiConfiguration(ignored -> seed).join();
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

  /**
   * Same as {@link #twoMemberCluster()}, plus a second, untouched partition group ("tenanta") that
   * no request in a given test targets.
   */
  private CurrentClusterConfiguration twoMemberClusterWithSecondGroup() {
    final var base = twoMemberCluster();
    final var tenantAGroup =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_0,
                BrokerPartitionState.initialize(
                    Map.of(2, PartitionState.active(1, partitionConfig)))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    return new CurrentClusterConfiguration(
        base.version(),
        base.globalConfiguration(),
        Map.of(
            CurrentClusterConfiguration.DEFAULT_GROUP,
            base.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP),
            "tenanta",
            tenantAGroup),
        base.phasedChangeState());
  }

  private CurrentClusterConfiguration configuration() {
    return manager.getMultiConfiguration().join();
  }

  @Test
  void shouldGenerateApplyAndAdvanceAMultiPhasePlan() {
    // given — a two-member cluster; the local member 0 is the coordinator
    wire(MEMBER_0, twoMemberCluster());
    // a request producing a global operation followed by a partition operation, both targeting
    // member 0 → a two-phase plan (global phase, then a default-group partition phase)
    final ConfigurationChangeRequest request =
        current ->
            Either.right(
                CurrentClusterConfiguration.toPhases(
                    List.of(
                        new PreScalingOperation(MEMBER_0, Set.of(MEMBER_0, MEMBER_1)),
                        new PartitionLeaveOperation(MEMBER_0, 1, 1))));

    // when
    final var result = coordinator.applyOperations(request).join();

    // then — the generated operations are returned, the plan drains phase by phase and completes
    assertThat(result.legacyOperations())
        .containsExactly(
            new PreScalingOperation(MEMBER_0, Set.of(MEMBER_0, MEMBER_1)),
            new PartitionLeaveOperation(MEMBER_0, 1, 1));

    final var config = configuration();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(config.phasedChangeState().lastChange())
        .hasValueSatisfying(
            last -> assertThat(last.status()).isEqualTo(PhasedChangePlanStatus.COMPLETED));
    assertThat(config.globalConfiguration().hasPendingChanges()).isFalse();
    final var defaultGroup = config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasPendingChanges()).isFalse();
    // member 0 left partition 1; member 1 still hosts it
    assertThat(defaultGroup.hasMember(MEMBER_0)).isFalse();
    assertThat(defaultGroup.hasMember(MEMBER_1)).isTrue();
  }

  @Test
  void shouldPreserveNonDefaultPartitionGroupInResultAndConfigurationAfterADefaultGroupChange() {
    // given — a cluster with two partition groups: "default" (targeted by this change) and
    // "tenanta" (untouched by any request in this test)
    final var seed = twoMemberClusterWithSecondGroup();
    final var tenantAGroupBefore = seed.partitionGroup("tenanta");
    wire(MEMBER_0, seed);
    final ConfigurationChangeRequest request =
        current ->
            Either.right(
                CurrentClusterConfiguration.toPhases(
                    List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1))));

    // when
    final var result = coordinator.applyOperations(request).join();

    // then — the coordinator's own result carries the full multi-group configuration, both
    // before and after the change, rather than silently projecting the non-default group away
    assertThat(result.currentMultiConfiguration().partitionGroup("tenanta"))
        .isEqualTo(tenantAGroupBefore);
    assertThat(result.finalMultiConfiguration().partitionGroup("tenanta"))
        .isEqualTo(tenantAGroupBefore);

    // and — the real post-apply configuration reflects the same: default group changed as
    // expected, tenanta is untouched
    final var config = configuration();
    assertThat(config.partitionGroup("tenanta")).isEqualTo(tenantAGroupBefore);
    final var defaultGroup = config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasMember(MEMBER_0)).isFalse();
    assertThat(defaultGroup.hasMember(MEMBER_1)).isTrue();
  }

  @Test
  void shouldRejectWhenNotCoordinator() {
    // given — the local member is 1, but member 0 (lower id) is the coordinator
    wire(MEMBER_1, twoMemberCluster());
    final ConfigurationChangeRequest request =
        current ->
            Either.right(
                CurrentClusterConfiguration.toPhases(
                    List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1))));

    // when / then — the non-coordinator refuses to apply the change
    assertThat(coordinator.applyOperations(request)).failsWithin(Duration.ofSeconds(5));
    assertThat(configuration().phasedChangeState().pending()).isEmpty();
  }

  @Test
  void shouldNotStartPlanOnDryRun() {
    // given
    wire(MEMBER_0, twoMemberCluster());
    final ConfigurationChangeRequest request =
        current ->
            Either.right(
                CurrentClusterConfiguration.toPhases(
                    List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1))));

    // when
    final var result = coordinator.simulateOperations(request).join();

    // then — the operations are returned but no plan is started
    assertThat(result.legacyOperations())
        .containsExactly(new PartitionLeaveOperation(MEMBER_0, 1, 1));
    assertThat(configuration().phasedChangeState().pending()).isEmpty();
    final var defaultGroup =
        configuration().partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasPendingChanges()).isFalse();
    assertThat(defaultGroup.hasMember(MEMBER_0)).isTrue();
  }

  @Test
  void shouldFailDryRunWithValidationError() {
    // given — member 0 leaving a partition it doesn't host (only partition 1 is assigned) is
    // rejected by the real PartitionGroupConfigurationChangeAppliersImpl dispatch table during
    // simulation
    wire(MEMBER_0, twoMemberCluster());
    final ConfigurationChangeRequest request =
        current ->
            Either.right(
                CurrentClusterConfiguration.toPhases(
                    List.of(new PartitionLeaveOperation(MEMBER_0, 2, 1))));

    // when
    final var simulationResult = coordinator.simulateOperations(request);

    // then — rejected during validation; no plan is started
    assertThat(simulationResult)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(InvalidRequest.class);
    assertThat(configuration().phasedChangeState().pending()).isEmpty();
  }

  @Test
  void shouldCompleteWithoutChangesWhenNoOperationsGenerated() {
    // given
    wire(MEMBER_0, twoMemberCluster());
    final ConfigurationChangeRequest request =
        current -> Either.right(CurrentClusterConfiguration.toPhases(List.of()));

    // when
    final var result = coordinator.applyOperations(request).join();

    // then
    assertThat(result.legacyOperations()).isEmpty();
    assertThat(configuration().phasedChangeState().pending()).isEmpty();
  }

  @Test
  void shouldRejectInvalidOperationUsingRealAppliers() {
    // given — a request whose second operation is invalid: member 0 leaves partition 1 twice,
    // but after the first leave it no longer hosts the partition. Validation must catch this via
    // the real PartitionGroupConfigurationChangeAppliersImpl dispatch table, not a legacy
    // simulator.
    wire(MEMBER_0, twoMemberCluster());
    final ConfigurationChangeRequest request =
        current ->
            Either.right(
                CurrentClusterConfiguration.toPhases(
                    List.of(
                        new PartitionLeaveOperation(MEMBER_0, 1, 1),
                        new PartitionLeaveOperation(MEMBER_0, 1, 1))));

    // when
    final var applyFuture = coordinator.applyOperations(request);

    // then — rejected during validation; no plan is started
    assertThat(applyFuture)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(InvalidRequest.class);
    assertThat(configuration().phasedChangeState().pending()).isEmpty();
    final var defaultGroup =
        configuration().partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasMember(MEMBER_0)).isTrue();
  }

  @Test
  void shouldRejectWhenAnotherChangeIsInProgress() {
    // given — a plan is already pending (its only operation targets member 1, so it never drains
    // on this member)
    wire(MEMBER_0, twoMemberCluster());
    manager
        .updateMultiConfiguration(
            c -> c.initPlan(List.of(new GlobalPhase(List.of(new MemberLeaveOperation(MEMBER_1))))))
        .join();
    final ConfigurationChangeRequest request =
        current ->
            Either.right(
                CurrentClusterConfiguration.toPhases(
                    List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1))));

    // when
    final var applyFuture = coordinator.applyOperations(request);

    // then — rejected because a change is already in progress
    assertThat(applyFuture)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(ConcurrentModificationException.class);
  }

  @Test
  void shouldCancelOngoingChange() {
    // given — a plan is pending; its only operation targets member 1, so on member 0 it never
    // drains on its own
    wire(MEMBER_0, twoMemberCluster());
    manager
        .updateMultiConfiguration(
            c -> c.initPlan(List.of(new GlobalPhase(List.of(new MemberLeaveOperation(MEMBER_1))))))
        .join();
    final var changeId = configuration().phasedChangeState().onlyPending().id();

    // when
    final var cancelled = coordinator.cancelChange(changeId).join();

    // then — the returned configuration and the real one both reflect the cancellation: no
    // pending plan, cleared on every sub-config that had one, marked CANCELLED
    assertThat(cancelled.phasedChangeState().pending()).isEmpty();
    final var config = configuration();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(config.globalConfiguration().hasPendingChanges()).isFalse();
    assertThat(config.phasedChangeState().lastChange())
        .hasValueSatisfying(
            last -> {
              assertThat(last.id()).isEqualTo(changeId);
              assertThat(last.status()).isEqualTo(PhasedChangePlanStatus.CANCELLED);
            });
  }

  @Test
  void shouldClearPendingChangesOnAllTargetedGroupsWhenCancelling() {
    // given — a plan whose first (and only) phase targets the default group and never drains
    // because its operation targets member 1
    wire(MEMBER_0, twoMemberCluster());
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        PartitionGroupPhase.sequential(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP,
                                List.of(new PartitionLeaveOperation(MEMBER_1, 1, 1)))))))
        .join();
    final var changeId = configuration().phasedChangeState().onlyPending().id();

    // when
    coordinator.cancelChange(changeId).join();

    // then
    final var defaultGroup =
        configuration().partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasPendingChanges()).isFalse();
  }

  @Test
  void shouldRejectCancelWhenNoChangeIsInProgress() {
    // given — no pending plan
    wire(MEMBER_0, twoMemberCluster());

    // when
    final var cancelFuture = coordinator.cancelChange(1);

    // then
    assertThat(cancelFuture)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldRejectCancelWithWrongChangeId() {
    // given — a plan is pending with some id
    wire(MEMBER_0, twoMemberCluster());
    manager
        .updateMultiConfiguration(
            c -> c.initPlan(List.of(new GlobalPhase(List.of(new MemberLeaveOperation(MEMBER_1))))))
        .join();
    final var changeId = configuration().phasedChangeState().onlyPending().id();

    // when — cancelling a different id
    final var cancelFuture = coordinator.cancelChange(changeId + 1);

    // then — rejected, and the pending plan is left untouched
    assertThat(cancelFuture)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(InvalidRequest.class);
    assertThat(configuration().phasedChangeState().pending()).isNotEmpty();
  }

  @Test
  void shouldRunConcurrentChangesOnDisjointPartitionGroupsAndCancelIndependently() {
    // given — a cluster with two independent partition groups: "default" and "tenanta". A change
    // is started on "default" that never drains on its own (its operation targets member 1)
    wire(MEMBER_0, twoMemberClusterWithSecondGroup());
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        PartitionGroupPhase.sequential(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP,
                                List.of(new PartitionLeaveOperation(MEMBER_1, 1, 1)))))))
        .join();
    final var defaultChangeId = configuration().phasedChangeState().onlyPending().id();

    // when — a second, independent change is admitted concurrently on "tenanta"
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        PartitionGroupPhase.sequential(
                            Map.of(
                                "tenanta", List.of(new PartitionLeaveOperation(MEMBER_0, 2, 1)))))))
        .join();
    final var pendingAfterBothStarted = configuration().phasedChangeState().pending();
    final var tenantaChangeId =
        pendingAfterBothStarted.keySet().stream()
            .filter(id -> id != defaultChangeId)
            .findFirst()
            .orElseThrow();

    // then — both plans are pending concurrently, each with its own distinct id
    assertThat(pendingAfterBothStarted).containsOnlyKeys(defaultChangeId, tenantaChangeId);

    // when — only the default-group change is cancelled
    coordinator.cancelChange(defaultChangeId).join();

    // then — the default group's change is gone, but tenanta's change is untouched and still
    // pending — cancelling one concurrent change must not affect the other
    final var afterCancellingDefault = configuration();
    assertThat(afterCancellingDefault.phasedChangeState().pending())
        .containsOnlyKeys(tenantaChangeId);
    assertThat(
            afterCancellingDefault
                .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                .hasPendingChanges())
        .isFalse();
    assertThat(afterCancellingDefault.partitionGroup("tenanta").hasPendingChanges()).isTrue();

    // and — the remaining tenanta change can independently be cancelled too
    coordinator.cancelChange(tenantaChangeId).join();
    assertThat(configuration().phasedChangeState().pending()).isEmpty();
    assertThat(configuration().partitionGroup("tenanta").hasPendingChanges()).isFalse();
  }

  @Test
  void shouldAllowApplyingANewRequestOnADisjointPartitionGroupWhileOneIsPending() {
    // given — a plan already pending on "tenanta", never draining on its own (no appliers are
    // registered for "tenanta" in this test's wiring)
    wire(MEMBER_0, twoMemberClusterWithSecondGroup());
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        PartitionGroupPhase.sequential(
                            Map.of(
                                "tenanta", List.of(new PartitionLeaveOperation(MEMBER_0, 2, 1)))))))
        .join();
    final var tenantaChangeId = configuration().phasedChangeState().onlyPending().id();
    final ConfigurationChangeRequest request =
        current ->
            Either.right(
                CurrentClusterConfiguration.toPhases(
                    List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1))));

    // when — a request targeting the disjoint "default" group is applied through the real
    // coordinator
    final var result = coordinator.applyOperations(request).join();

    // then — admitted with its own distinct id and drains to completion; the tenanta plan is
    // untouched throughout
    assertThat(result.changeId()).isNotEqualTo(tenantaChangeId);
    final var config = configuration();
    assertThat(config.phasedChangeState().pending()).containsOnlyKeys(tenantaChangeId);
    assertThat(config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).hasPendingChanges())
        .isFalse();
    assertThat(config.partitionGroup("tenanta").hasPendingChanges()).isTrue();
  }

  @Test
  void shouldRejectGlobalScopedRequestWhilePartitionGroupChangeIsPending() {
    // given — a plan pending on the default group only
    wire(MEMBER_0, twoMemberCluster());
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        PartitionGroupPhase.sequential(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP,
                                List.of(new PartitionLeaveOperation(MEMBER_1, 1, 1)))))))
        .join();
    final ConfigurationChangeRequest request =
        current ->
            Either.right(
                CurrentClusterConfiguration.toPhases(List.of(new MemberLeaveOperation(MEMBER_1))));

    // when — a global-scoped request is applied while the group-scoped one is still pending
    final var applyFuture = coordinator.applyOperations(request);

    // then — rejected: a global-scoped change conflicts with everything, even a disjoint group
    assertThat(applyFuture)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(ConcurrentModificationException.class);
  }

  @Test
  void shouldRejectPartitionGroupRequestWhileGlobalChangeIsPending() {
    // given — a global-scoped plan is pending
    wire(MEMBER_0, twoMemberCluster());
    manager
        .updateMultiConfiguration(
            c -> c.initPlan(List.of(new GlobalPhase(List.of(new MemberLeaveOperation(MEMBER_1))))))
        .join();
    final ConfigurationChangeRequest request =
        current ->
            Either.right(
                CurrentClusterConfiguration.toPhases(
                    List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1))));

    // when — a group-scoped request is applied while the global one is still pending
    final var applyFuture = coordinator.applyOperations(request);

    // then — rejected: everything conflicts with a pending global-scoped change
    assertThat(applyFuture)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(ConcurrentModificationException.class);
  }

  /**
   * Same as {@link #twoMemberClusterWithSecondGroup()}, except the second group ("tenanta") is
   * disabled.
   */
  private CurrentClusterConfiguration twoMemberClusterWithDisabledSecondGroup() {
    final var base = twoMemberClusterWithSecondGroup();
    final var disabledTenantA = base.partitionGroup("tenanta").disable();
    return new CurrentClusterConfiguration(
        base.version(),
        base.globalConfiguration(),
        Map.of(
            CurrentClusterConfiguration.DEFAULT_GROUP,
            base.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP),
            "tenanta",
            disabledTenantA),
        base.phasedChangeState());
  }

  @Test
  void shouldExcludeDisabledPartitionGroupFromPhasesByDefault() {
    // given — a cluster with an active "default" group and a disabled "tenanta" group
    wire(MEMBER_0, twoMemberClusterWithDisabledSecondGroup());
    final CurrentClusterConfiguration[] seenByPhases = new CurrentClusterConfiguration[1];
    final ConfigurationChangeRequest request =
        new ConfigurationChangeRequest() {
          @Override
          public Either<Exception, List<Phase>> phases(
              final CurrentClusterConfiguration clusterConfiguration) {
            seenByPhases[0] = clusterConfiguration;
            return Either.right(CurrentClusterConfiguration.toPhases(List.of()));
          }
        };

    // when
    coordinator.applyOperations(request).join();

    // then — the request only sees the active group; the disabled one is filtered out
    assertThat(seenByPhases[0].partitionGroups().keySet())
        .containsExactly(CurrentClusterConfiguration.DEFAULT_GROUP);
  }

  @Test
  void shouldPassDisabledPartitionGroupsWhenRequestOptsIn() {
    // given — a cluster with an active "default" group and a disabled "tenanta" group
    wire(MEMBER_0, twoMemberClusterWithDisabledSecondGroup());
    final CurrentClusterConfiguration[] seenByPhases = new CurrentClusterConfiguration[1];
    final ConfigurationChangeRequest request =
        new ConfigurationChangeRequest() {
          @Override
          public Either<Exception, List<Phase>> phases(
              final CurrentClusterConfiguration clusterConfiguration) {
            seenByPhases[0] = clusterConfiguration;
            return Either.right(CurrentClusterConfiguration.toPhases(List.of()));
          }

          @Override
          public boolean applyToDisabledTenants() {
            return true;
          }
        };

    // when
    coordinator.applyOperations(request).join();

    // then — the request opted in, so it sees the disabled group too
    assertThat(seenByPhases[0].partitionGroups().keySet())
        .containsExactlyInAnyOrder(CurrentClusterConfiguration.DEFAULT_GROUP, "tenanta");
  }
}
