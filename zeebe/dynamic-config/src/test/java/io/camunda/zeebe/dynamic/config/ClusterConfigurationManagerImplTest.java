/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.StaticInitializer;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor.NoopRestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.BrokerState.State;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link ClusterConfigurationManagerImpl}'s multi-partition-group apply loop: local
 * operation application, phase/plan advancement, gossip merge and re-publish, inconsistency
 * detection, retry-on-failure, and start-up (including resuming a change plan across a restart).
 */
final class ClusterConfigurationManagerImplTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");
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
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));
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
                        PartitionGroupPhase.sequential(
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
  void shouldApplyEveryOperationInASingleGroupPhaseSequentially() {
    // given — member 1 replicates partitions 1 and 2; a phase joins the local member (0) to both,
    // the ordinary scale-up shape (toPhases folds a run of same-kind ops into one phase, so a
    // multi-partition scale-up is one phase with several operations, not several phases)
    final var manager = newManager(MEMBER_0);
    final var group =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_1,
                BrokerPartitionState.initialize(
                    Map.of(
                        1, PartitionState.active(1, partitionConfig),
                        2, PartitionState.active(1, partitionConfig)))),
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

    // when — one phase carries two join operations for the local member, one per partition
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        PartitionGroupPhase.sequential(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP,
                                List.of(
                                    new PartitionJoinOperation(MEMBER_0, 1, 1, true),
                                    new PartitionJoinOperation(MEMBER_0, 2, 1, true)))))))
        .join();

    // then — both operations drained in order and the plan completed; a reconciliation loop that
    // stopped after the first operation would leave partition 2 (and the plan) stuck pending
    final var config = configuration(manager);
    final var defaultGroup = config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasPendingChanges()).isFalse();
    assertThat(defaultGroup.getMember(MEMBER_0).partitions()).containsOnlyKeys(1, 2);
    assertThat(config.phasedChangeState().pending()).isEmpty();
    final var lastChange = config.phasedChangeState().lastChange();
    assertThat(lastChange).isPresent();
    assertThat(lastChange.get().status()).isEqualTo(PhasedChangePlanStatus.COMPLETED);
  }

  @Test
  void shouldApplyOneParallelPhaseToEveryPartitionGroupItTargets() {
    // given — two partition groups (physical tenants), each with the local member replicating its
    // own partition 1. A cluster purge plans one parallel phase spanning every group, so a phase
    // that only drained in the default group would leave the other tenant untouched.
    final var manager = newManager(MEMBER_0);
    manager.registerPartitionGroupChangeAppliers(
        "tenanta",
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));

    final var group =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_0,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, partitionConfig))),
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
            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, group, "tenanta", group),
            PhasedChangeState.empty());
    manager.updateMultiConfiguration(ignored -> seeded).join();

    // when — one phase carries the local member's leave operation for both groups
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        PartitionGroupPhase.sequential(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP,
                                List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1)),
                                "tenanta",
                                List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1)))))))
        .join();

    // then — both groups drained their side of the phase
    final var config = configuration(manager);
    for (final var groupId : List.of(CurrentClusterConfiguration.DEFAULT_GROUP, "tenanta")) {
      assertThat(config.partitionGroup(groupId).hasPendingChanges())
          .describedAs("pending changes of group '%s'", groupId)
          .isFalse();
      assertThat(config.partitionGroup(groupId).hasMember(MEMBER_0))
          .describedAs("member 0 left group '%s'", groupId)
          .isFalse();
    }
  }

  @Test
  void shouldApplyEveryRunnableGraphOperationOfTheLocalMember() {
    // given — a graph phase whose two operations have no edge between them, so both are runnable
    // from the start. Under the queue the local member would have applied one, gossiped, and only
    // then been offered the second.
    final var manager = newManager(MEMBER_0);
    manager.updateMultiConfiguration(ignored -> twoPartitionCluster()).join();
    final var graph = OperationGraph.builder();
    graph.add(new PartitionLeaveOperation(MEMBER_0, 1, 1));
    graph.add(new PartitionLeaveOperation(MEMBER_0, 2, 1));

    // when
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        new PartitionGroupPhase(
                            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, graph.build())))))
        .join();

    // then — both ran and the graph drained, without any peer having to move
    final var defaultGroup =
        configuration(manager).partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.pendingChanges()).isEmpty();
    assertThat(defaultGroup.hasMember(MEMBER_0)).isFalse();
  }

  @Test
  void shouldNotApplyAGraphOperationWhoseDependencyHasNotCompleted() {
    // given — the local member's operation waits on a peer's, which nothing here will run. This is
    // what keeps a declared edge meaningful: without it the local member would apply its own
    // operation as soon as it saw the plan.
    final var manager = newManager(MEMBER_0);
    manager.updateMultiConfiguration(ignored -> twoPartitionCluster()).join();
    final var graph = OperationGraph.builder();
    final var peerOperation = graph.add(new PartitionLeaveOperation(MEMBER_1, 1, 1));
    graph.add(new PartitionLeaveOperation(MEMBER_0, 2, 1), Set.of(peerOperation));

    // when
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        new PartitionGroupPhase(
                            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, graph.build())))))
        .join();

    // then — nothing was applied and both operations are still outstanding
    final var defaultGroup =
        configuration(manager).partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.pendingChanges().orElseThrow().completed()).isEmpty();
    assertThat(defaultGroup.getMember(MEMBER_0).partitions()).containsOnlyKeys(1, 2);
  }

  @Test
  void shouldNotExceedTheConcurrencyCapAcrossReentrantReconciliation() {
    // given — 6 independent operations on one broker, well above the concurrency cap (4), each
    // held open (never resolving) so the count of simultaneously-applying operations can be
    // observed. Staging an operation persists synchronously, which re-enters reconcile() before
    // the operation that triggered it has itself been counted — a cap tracked as a count local to
    // one reconcile() call resets to zero on every such nesting and never actually bounds
    // anything, which is exactly the shape this pins.
    final var member1 = MemberId.from("1");
    final var startedCount = new AtomicInteger();
    final var peakConcurrent = new AtomicInteger();
    final var manager = newManager(MEMBER_0);
    final var real =
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor());
    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        operation -> {
          final var delegate = real.getApplier(operation);
          return new PartitionGroupConfigurationChangeApplier() {
            @Override
            public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
                final GlobalConfiguration global, final PartitionGroupConfiguration group) {
              return delegate.init(global, group);
            }

            @Override
            public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
              peakConcurrent.updateAndGet(ignored -> startedCount.incrementAndGet());
              // Never completes: a real (I/O-bound) applier stays in flight for a while too, and
              // that is the window the cap is meant to hold.
              return new CompletableActorFuture<>();
            }
          };
        });

    final Map<Integer, PartitionState> partitions = new HashMap<>();
    for (int p = 1; p <= 6; p++) {
      partitions.put(p, PartitionState.active(1, partitionConfig));
    }
    manager
        .updateMultiConfiguration(
            ignored ->
                new CurrentClusterConfiguration(
                    CurrentClusterConfiguration.INITIAL_VERSION,
                    new GlobalConfiguration(
                        1,
                        Optional.empty(),
                        Map.of(
                            MEMBER_0, new BrokerState(0, Instant.EPOCH, State.ACTIVE),
                            member1, new BrokerState(0, Instant.EPOCH, State.ACTIVE)),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                    Map.of(
                        CurrentClusterConfiguration.DEFAULT_GROUP,
                        new PartitionGroupConfiguration(
                            1,
                            0,
                            Map.of(
                                MEMBER_0, BrokerPartitionState.initialize(partitions),
                                member1, BrokerPartitionState.initialize(partitions)),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty())),
                    PhasedChangeState.empty()))
        .join();

    // when — member 0 leaves all 6 of its own replicas; each partition keeps member 1's replica,
    // so every leave is independently valid and none depends on another
    final var graph = OperationGraph.builder();
    for (int p = 1; p <= 6; p++) {
      graph.add(new PartitionLeaveOperation(MEMBER_0, p, 1));
    }
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        new PartitionGroupPhase(
                            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, graph.build())))))
        .join();

    // then — never more than the cap were simultaneously applying, however deep the reentrant
    // cascade of staging one operation into the next went
    assertThat(peakConcurrent).hasValueLessThanOrEqualTo(4);
  }

  @Test
  void shouldNotLetACancelledPlansInFlightOperationBlockItsReplacement() {
    // given — operation ids restart at 0 for every fresh graph. Cancelling a plan while one of its
    // operations is still applying (async, real work, never resolving here -- the "stuck change"
    // that gets a plan cancelled in the first place), then starting a replacement on the same
    // group, is the case: the replacement's own id-0 operation is runnable, and must not be taken
    // for the cancelled plan's id-0 operation that is still in flight and may never finish.
    final var member1 = MemberId.from("1");
    final var pendingApplies =
        new HashMap<Integer, CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>>>();
    final var applied = new java.util.ArrayList<Integer>();
    final var manager = newManager(MEMBER_0);
    final var real =
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor());
    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        operation -> {
          final var partitionOperation = (PartitionLeaveOperation) operation;
          final var delegate = real.getApplier(operation);
          return new PartitionGroupConfigurationChangeApplier() {
            @Override
            public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
                final GlobalConfiguration global, final PartitionGroupConfiguration group) {
              return delegate.init(global, group);
            }

            @Override
            public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
              applied.add(partitionOperation.partitionId());
              final var future =
                  new CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>>();
              pendingApplies.put(partitionOperation.partitionId(), future);
              return future;
            }
          };
        });

    final var replicated =
        BrokerPartitionState.initialize(
            Map.of(
                1, PartitionState.active(1, partitionConfig),
                2, PartitionState.active(1, partitionConfig)));
    manager
        .updateMultiConfiguration(
            ignored ->
                new CurrentClusterConfiguration(
                    CurrentClusterConfiguration.INITIAL_VERSION,
                    new GlobalConfiguration(
                        1,
                        Optional.empty(),
                        Map.of(
                            MEMBER_0, new BrokerState(0, Instant.EPOCH, State.ACTIVE),
                            member1, new BrokerState(0, Instant.EPOCH, State.ACTIVE)),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                    Map.of(
                        CurrentClusterConfiguration.DEFAULT_GROUP,
                        new PartitionGroupConfiguration(
                            1,
                            0,
                            Map.of(MEMBER_0, replicated, member1, replicated),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty())),
                    PhasedChangeState.empty()))
        .join();

    // when — plan A leaves partition 1; its single operation gets id 0 and is still applying
    // (its future is deliberately left unresolved) when the plan is cancelled
    final var planAGraph = OperationGraph.builder();
    planAGraph.add(new PartitionLeaveOperation(MEMBER_0, 1, 1));
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        new PartitionGroupPhase(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP, planAGraph.build())))))
        .join();
    assertThat(applied).containsExactly(1);
    final var planAId =
        configuration(manager).phasedChangeState().pending().keySet().iterator().next();
    manager.updateMultiConfiguration(c -> c.cancelPendingChanges(planAId)).join();

    // when — plan B leaves partition 2 instead; its single operation is also id 0 (a fresh graph
    // numbers from zero)
    final var planBGraph = OperationGraph.builder();
    planBGraph.add(new PartitionLeaveOperation(MEMBER_0, 2, 1));
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        new PartitionGroupPhase(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP, planBGraph.build())))))
        .join();

    // then — it starts immediately, without waiting on plan A's operation. In-flight state is keyed
    // by (plan id, operation id), so the shared id 0 is not a collision; keyed by the bare id it
    // would be, and plan B would be stuck for as long as plan A's operation stayed unresolved --
    // forever, if it is genuinely hung, since nothing else reclaims that entry.
    assertThat(applied)
        .describedAs("plan B's operation is not blocked by plan A's stale in-flight id")
        .containsExactly(1, 2);
    assertThat(pendingApplies.get(1).isDone())
        .describedAs("plan A's operation is still unresolved")
        .isFalse();
  }

  /** Both members replicate partitions 1 and 2, so either may leave either partition. */
  private CurrentClusterConfiguration twoPartitionCluster() {
    final var replicated =
        BrokerPartitionState.initialize(
            Map.of(
                1, PartitionState.active(1, partitionConfig),
                2, PartitionState.active(1, partitionConfig)));
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
        Map.of(
            CurrentClusterConfiguration.DEFAULT_GROUP,
            new PartitionGroupConfiguration(
                1,
                0,
                Map.of(MEMBER_0, replicated, MEMBER_1, replicated),
                Optional.empty(),
                Optional.empty(),
                Optional.empty())),
        PhasedChangeState.empty());
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
    final var pending = config.phasedChangeState().onlyPending();
    assertThat(pending.currentPhaseIndex()).isZero();
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
                        PartitionGroupPhase.sequential(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP,
                                List.of(new PartitionJoinOperation(MEMBER_0, 1, 1, true)))))))
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
  void shouldAdvanceThroughTwoPhasesTargetingDisjointPartitionGroups() {
    // given — member 0 is the coordinator; two physical tenants ("a" and "b") each hold one
    // partition replicated only by member 1
    final var manager = newManager(MEMBER_0);
    manager.registerPartitionGroupChangeAppliers(
        "a",
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));
    manager.registerPartitionGroupChangeAppliers(
        "b",
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));

    final var groupWithMember1 =
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
                Map.of(
                    MEMBER_0, new BrokerState(0, Instant.EPOCH, State.ACTIVE),
                    MEMBER_1, new BrokerState(0, Instant.EPOCH, State.ACTIVE)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()),
            Map.of("a", groupWithMember1, "b", groupWithMember1),
            PhasedChangeState.empty());
    manager.updateMultiConfiguration(ignored -> seeded).join();

    // when — a two-phase plan adds member 0 as a replica of group "a"'s partition 1, then group
    // "b"'s partition 1: each phase targets exactly one, disjoint group — the shape a
    // stray-double-advance would corrupt silently, by activating phase 1 without phase 0 having
    // actually drained, or by skipping straight past phase 1 without ever activating it
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        PartitionGroupPhase.sequential(
                            Map.of("a", List.of(new PartitionJoinOperation(MEMBER_0, 1, 1, true)))),
                        PartitionGroupPhase.sequential(
                            Map.of(
                                "b", List.of(new PartitionJoinOperation(MEMBER_0, 1, 1, true)))))))
        .join();

    // then — both phases were applied in order and the plan completed; neither group was skipped
    final var config = configuration(manager);
    assertThat(config.partitionGroup("a").hasMember(MEMBER_0)).isTrue();
    assertThat(config.partitionGroup("b").hasMember(MEMBER_0)).isTrue();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    final var lastChange = config.phasedChangeState().lastChange();
    assertThat(lastChange).isPresent();
    assertThat(lastChange.get().status()).isEqualTo(PhasedChangePlanStatus.COMPLETED);
  }

  @Test
  void shouldMergeAndApplyOperationOnGossipReceived() {
    // given — the local member (1) starts with an empty, initialized configuration
    final var manager = newManager(MEMBER_1);
    manager
        .start(() -> CompletableActorFuture.completed(CurrentClusterConfiguration.init()))
        .join();

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
  void shouldDetectInconsistencyWhenLocalMemberStateDiffersInAnyPartitionGroup() {
    // given — member 0 replicates partition 1 in the "default" group, and partition 2 in a second
    // group ("tenanta")
    final var manager = newManager(MEMBER_0);
    final var defaultGroup =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_0,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, partitionConfig))),
                MEMBER_1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(2, partitionConfig)))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
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
    final var global =
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(
                MEMBER_0, new BrokerState(0, Instant.EPOCH, State.ACTIVE),
                MEMBER_1, new BrokerState(0, Instant.EPOCH, State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var seeded =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(
                CurrentClusterConfiguration.DEFAULT_GROUP, defaultGroup, "tenanta", tenantAGroup),
            PhasedChangeState.empty());
    manager.start(() -> CompletableActorFuture.completed(seeded)).join();

    final var newConfigSeen = new AtomicReference<CurrentClusterConfiguration>();
    final var oldConfigSeen = new AtomicReference<CurrentClusterConfiguration>();
    manager.registerTopologyChangedListener(
        (newConfiguration, oldConfiguration) -> {
          newConfigSeen.set(newConfiguration);
          oldConfigSeen.set(oldConfiguration);
        });

    // when — a force-scale-down is received via gossip: member 0 is stripped out of the "tenanta"
    // group's configuration without its own participation (the "default" group is untouched)
    final var forcedTenantAGroup =
        new PartitionGroupConfiguration(
            2, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.empty());
    final var received =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(
                CurrentClusterConfiguration.DEFAULT_GROUP,
                defaultGroup,
                "tenanta",
                forcedTenantAGroup),
            PhasedChangeState.empty());
    manager.onGossipReceivedCurrent(received);

    // then — the inconsistency listener fires even though only a non-default group changed
    Awaitility.await("Inconsistency listener is invoked")
        .untilAsserted(() -> assertThat(newConfigSeen.get()).isNotNull());
    assertThat(oldConfigSeen.get().partitionGroup("tenanta").members()).containsKey(MEMBER_0);
    assertThat(newConfigSeen.get().partitionGroup("tenanta").members()).isEmpty();
  }

  @Test
  void shouldNotDetectInconsistencyWhenMemberLeavesGroupAsPartOfCurrentPlan() {
    // given — member 0 replicates partition 1 in the "default" group, and a plan is under way
    // whose (unmutated) phase 0 has member 0 leaving that group as one of its steps. No partition
    // group applier is registered, so the manager cannot apply this operation locally itself --
    // simulating the race where a peer's gossip reports the completion before the local apply
    // would otherwise catch up.
    final var persisted =
        PersistedCurrentClusterConfiguration.ofFile(
            tmp.resolve("config-no-appliers.meta"), new ProtoBufSerializer());
    final var manager =
        new ClusterConfigurationManagerImpl(
            executor,
            MEMBER_0,
            persisted,
            new TopologyManagerMetrics(new SimpleMeterRegistry()),
            Duration.ofMillis(1),
            Duration.ofMillis(1));
    manager.setCurrentConfigurationGossiper(ignored -> {});
    final var leaveOperation = new PartitionLeaveOperation(MEMBER_0, 1, 1);
    final var group =
        new PartitionGroupConfiguration(
            2,
            0,
            Map.of(
                MEMBER_0,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, partitionConfig))),
                MEMBER_1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(2, partitionConfig)))),
            Optional.empty(),
            // The phase has been activated: its operation is copied into the group's own pending
            // changes, matching what applyPhase does. Without this, maybeAdvancePhase would see the
            // group as trivially drained (no pending changes) and immediately complete the whole
            // plan before the gossip-receive below even runs.
            Optional.of(DependencyChangePlan.sequential(1, List.of(leaveOperation))),
            Optional.empty());
    final var global =
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(
                MEMBER_0, new BrokerState(0, Instant.EPOCH, State.ACTIVE),
                MEMBER_1, new BrokerState(0, Instant.EPOCH, State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var plan =
        PhasedChangePlan.init(
            1,
            List.of(
                PartitionGroupPhase.sequential(
                    Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, List.of(leaveOperation)))),
            Instant.EPOCH);
    final var seeded =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, group),
            new PhasedChangeState(plan.id() + 1, Map.of(plan.id(), plan), List.of()));
    manager.start(() -> CompletableActorFuture.completed(seeded)).join();

    final var listenerCalled = new AtomicBoolean(false);
    manager.registerTopologyChangedListener(
        (newConfiguration, oldConfiguration) -> listenerCalled.set(true));

    // when — a faster peer's gossip reports member 0 already pruned from the group (its
    // zero-partition entry removed) before the local apply of its own leave operation catches up;
    // the overall plan (phase 0, unmutated) still lists member 0 as one of the phase's operations
    final var groupWithoutMember0 =
        new PartitionGroupConfiguration(
            3,
            0,
            Map.of(
                MEMBER_1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(2, partitionConfig)))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var received =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, groupWithoutMember0),
            new PhasedChangeState(plan.id() + 1, Map.of(plan.id(), plan), List.of()));
    manager.onGossipReceivedCurrent(received);

    // then — the merge is applied (member 0 is gone from the group), but no inconsistency is
    // reported, since member 0 was itself an operation-target in the group's current plan
    Awaitility.await("Configuration is merged")
        .untilAsserted(
            () ->
                assertThat(
                        configuration(manager)
                            .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                            .hasMember(MEMBER_0))
                    .isFalse());
    assertThat(listenerCalled).describedAs("Inconsistency listener is never invoked").isFalse();
  }

  @Test
  void shouldNotDetectInconsistencyWhenNoPartitionGroupChangesForLocalMember() {
    // given — member 0 replicates partition 1 in the "default" group
    final var manager = newManager(MEMBER_0);
    final var defaultGroup =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_0,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, partitionConfig))),
                MEMBER_1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(2, partitionConfig)))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var global =
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(
                MEMBER_0, new BrokerState(0, Instant.EPOCH, State.ACTIVE),
                MEMBER_1, new BrokerState(0, Instant.EPOCH, State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var seeded =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, defaultGroup),
            PhasedChangeState.empty());
    manager.start(() -> CompletableActorFuture.completed(seeded)).join();

    final var listenerCalled = new AtomicBoolean(false);
    manager.registerTopologyChangedListener(
        (newConfiguration, oldConfiguration) -> listenerCalled.set(true));

    // when — a gossip update changes only member 1's state; member 0's state is untouched in every
    // group
    final var newBrokerState = new BrokerState(1, Instant.EPOCH, State.LEAVING);
    final var receivedGlobal =
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(
                MEMBER_0,
                new BrokerState(0, Instant.EPOCH, State.ACTIVE),
                MEMBER_1,
                newBrokerState),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var received =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            receivedGlobal,
            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, defaultGroup),
            PhasedChangeState.empty());
    manager.onGossipReceivedCurrent(received);

    // then
    Awaitility.await("Configuration is merged")
        .untilAsserted(
            () ->
                assertThat(configuration(manager).globalConfiguration().getMember(MEMBER_1).state())
                    .isEqualTo(State.LEAVING));
    assertThat(listenerCalled).describedAs("Inconsistency listener is never invoked").isFalse();
  }

  @Test
  void shouldRetryPendingOperationInPartitionGroupIfFailed() {
    // given — member 0 is the coordinator; it is part of the cluster and the default group, which
    // has member 1 holding partition 1; a plan is initiated to add member 0 as a replica of
    // partition 1, but the operation fails (e.g., due to a network error)
    final var manager = newManager(MEMBER_0);
    final var group =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_0,
                BrokerPartitionState.initialize(Map.of()),
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

    // Simulate failure of the operation (e.g., due to network error)
    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        new PartitionGroupConfigurationChangeAppliersImpl(
            new FailingExecutor(1),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));

    // when — a partition-group phase adds member 0 as a replica of partition 1, but the operation
    // fails; then the plan is retried
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        PartitionGroupPhase.sequential(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP,
                                List.of(new PartitionJoinOperation(MEMBER_0, 1, 1, true)))))))
        .join();

    // then — the operation was retried and completed successfully
    Awaitility.await("Pending operation should be retried and completed")
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final var config = configuration(manager);
              assertThat(
                      config
                          .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                          .hasPendingChanges())
                  .isFalse();
              assertThat(config.phasedChangeState().pending()).isEmpty();
            });
    final var config = configuration(manager);
    final var defaultGroup = config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasMember(MEMBER_0))
        .describedAs("Member 0 is added to the default group")
        .isTrue();
    assertThat(defaultGroup.hasPendingChanges()).isFalse();
    assertThat(config.phasedChangeState().pending()).isEmpty();
  }

  @Test
  void shouldRetryPendingOperationInPartitionGroupMultipleTimesBeforeSucceeding() {
    // given — same setup as shouldRetryPendingOperationInPartitionGroupIfFailed, but the
    // operation fails three times in a row before eventually succeeding — the previously
    // untested case where the retry itself also fails, more than once
    final var manager = newManager(MEMBER_0);
    final var group =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_0,
                BrokerPartitionState.initialize(Map.of()),
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

    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        new PartitionGroupConfigurationChangeAppliersImpl(
            new FailingExecutor(3),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));

    // when
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(
                        PartitionGroupPhase.sequential(
                            Map.of(
                                CurrentClusterConfiguration.DEFAULT_GROUP,
                                List.of(new PartitionJoinOperation(MEMBER_0, 1, 1, true)))))))
        .join();

    // then — three consecutive failures did not stop the retries; the fourth attempt completed
    Awaitility.await("Pending operation should be retried until it succeeds")
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final var config = configuration(manager);
              assertThat(
                      config
                          .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                          .hasPendingChanges())
                  .isFalse();
              assertThat(config.phasedChangeState().pending()).isEmpty();
            });
    final var defaultGroup =
        configuration(manager).partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasMember(MEMBER_0))
        .describedAs("Member 0 is added to the default group despite three failed attempts")
        .isTrue();
  }

  @Test
  void shouldRetryPendingOperationInGlobalPhaseIfFailed() {
    // given — member 0 is the coordinator; it is not yet part of the cluster; a plan is initiated
    // to add member 0 to the cluster, but the operation fails (e.g., due to a network error)
    final var manager = newManager(MEMBER_0);
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
            Map.of(),
            PhasedChangeState.empty());
    manager.updateMultiConfiguration(ignored -> seeded).join();

    // Simulate failure of the operation (e.g., due to network error)
    manager.registerGlobalChangeAppliers(
        new GlobalConfigurationChangeAppliersImpl(
            new FailingExecutor(1), new NoopClusterChangeExecutor()));

    // when — a global phase adds member 0 to the cluster, but the operation fails; then the plan is
    // retried
    manager
        .updateMultiConfiguration(
            c -> c.initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0))))))
        .join();

    // then — the operation was retried and completed successfully
    Awaitility.await("Pending operation should be retried and completed")
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final var config = configuration(manager);
              assertThat(config.globalConfiguration().hasPendingChanges()).isFalse();
              assertThat(config.phasedChangeState().pending()).isEmpty();
            });
    final var config = configuration(manager);
    assertThat(config.globalConfiguration().getMember(MEMBER_0).state())
        .describedAs("Member 0 is added to the cluster")
        .isEqualTo(State.ACTIVE);
    assertThat(config.globalConfiguration().hasPendingChanges()).isFalse();
    assertThat(config.phasedChangeState().pending()).isEmpty();
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
            List.of(new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 1)),
            1,
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, partitionConfig),
            "cluster-x");

    // when
    manager
        .start(new StaticInitializer<>(staticConfiguration::generateCurrentClusterConfiguration))
        .join();

    // then — the generated multi-group configuration was persisted and is retrievable
    final var config = configuration(manager);
    assertThat(config.globalConfiguration().members().keySet())
        .containsExactlyInAnyOrder(MEMBER_0, MEMBER_1);
    assertThat(config.globalConfiguration().clusterId()).contains("cluster-x");
    assertThat(config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).members().keySet())
        .containsExactlyInAnyOrder(MEMBER_0, MEMBER_1);
  }

  @Test
  void shouldGossipInitialConfigurationAfterStart() {
    // given — a manager whose gossiper is captured instead of the shared no-op one
    final var manager = newManager(MEMBER_0);
    final var gossiped = new AtomicReference<CurrentClusterConfiguration>();
    manager.setCurrentConfigurationGossiper(gossiped::set);
    final var initialConfiguration =
        CurrentClusterConfiguration.init()
            .updateGlobalConfiguration(
                g -> g.addMember(MEMBER_0, new BrokerState(0, Instant.EPOCH, State.ACTIVE)));

    // when
    manager.start(() -> CompletableActorFuture.completed(initialConfiguration)).join();

    // then — the initialized configuration was gossiped out
    assertThat(gossiped.get()).isNotNull();
    assertThat(gossiped.get().globalConfiguration().getMember(MEMBER_0).state())
        .isEqualTo(State.ACTIVE);
  }

  @Test
  void shouldFailToStartIfInitializationThrowsError() {
    // given
    final var manager = newManager(MEMBER_0);
    final ClusterConfigurationInitializer<CurrentClusterConfiguration> failingInitializer =
        () -> CompletableActorFuture.completedExceptionally(new RuntimeException("Expected"));

    // when
    final var startFuture = manager.start(failingInitializer);

    // then
    assertThat(startFuture).failsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldFailToStartIfConfigurationIsNotInitialized() {
    // given
    final var manager = newManager(MEMBER_0);
    final ClusterConfigurationInitializer<CurrentClusterConfiguration> uninitializedInitializer =
        () -> CompletableActorFuture.completed(CurrentClusterConfiguration.uninitialized());

    // when
    final var startFuture = manager.start(uninitializedInitializer);

    // then
    assertThat(startFuture).failsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldGossipAndPersistMergedConfigurationOnGossipReceived() {
    // given — the local member starts with an initialized, empty configuration
    final var manager = newManager(MEMBER_0);
    final var gossiped = new AtomicReference<CurrentClusterConfiguration>();
    manager.setCurrentConfigurationGossiper(gossiped::set);
    manager
        .start(() -> CompletableActorFuture.completed(CurrentClusterConfiguration.init()))
        .join();
    gossiped.set(null); // only interested in the gossip triggered by the gossip receipt below

    // when — a configuration received via gossip introduces a member the local manager doesn't
    // know about yet
    final var received =
        CurrentClusterConfiguration.init()
            .updateGlobalConfiguration(
                g -> g.addMember(MEMBER_1, new BrokerState(0, Instant.EPOCH, State.ACTIVE)));
    manager.onGossipReceivedCurrent(received);

    // then — the merged configuration is persisted locally and re-gossiped onward
    Awaitility.await("Configuration is merged and re-gossiped")
        .untilAsserted(
            () ->
                assertThat(configuration(manager).globalConfiguration().hasMember(MEMBER_1))
                    .isTrue());
    assertThat(gossiped.get()).isNotNull();
    assertThat(gossiped.get().globalConfiguration().hasMember(MEMBER_1)).isTrue();
  }

  @Test
  void shouldNotUpdateLocalConfigurationOnGossipReceivedBeforeInitialization() {
    // given — a manager that has not been started yet (no configuration has been initialized)
    final var manager = newManager(MEMBER_0);
    final var gossiped = new AtomicReference<CurrentClusterConfiguration>();
    manager.setCurrentConfigurationGossiper(gossiped::set);
    final var received =
        CurrentClusterConfiguration.init()
            .updateGlobalConfiguration(
                g -> g.addMember(MEMBER_1, new BrokerState(0, Instant.EPOCH, State.ACTIVE)));

    // when
    manager.onGossipReceivedCurrent(received);

    // then — the received configuration is relayed onward as-is (so peers still converge), but the
    // local (uninitialized) configuration is left untouched; updating it here would race with a
    // concurrently running initializer
    assertThat(gossiped.get()).isEqualTo(received);
    assertThat(configuration(manager).isUninitialized()).isTrue();
  }

  @Test
  void shouldContinueTopologyChangeOnRestart() {
    // given — the initializer returns a configuration that already has a pending plan targeting
    // the local member, as if the manager had restarted mid-change; partition 1 has two replicas
    // (members 0 and 1) and the plan removes member 0's replica (min allowed replicas = 1).
    // Partition-group appliers are deliberately NOT registered yet: in production they only
    // become available once local partitions are bootstrapped, which happens after start()
    // completes (see ClusterConfigurationManagerService#registerPartitionGroupChangeAppliers).
    final var persisted =
        PersistedCurrentClusterConfiguration.ofFile(
            tmp.resolve("config-restart.meta"), new ProtoBufSerializer());
    final var manager =
        new ClusterConfigurationManagerImpl(
            executor,
            MEMBER_0,
            persisted,
            new TopologyManagerMetrics(new SimpleMeterRegistry()),
            Duration.ofMillis(1),
            Duration.ofMillis(1));
    manager.setCurrentConfigurationGossiper(ignored -> {});
    manager.registerGlobalChangeAppliers(
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor()));
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
    final var withPendingPlan =
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
                PhasedChangeState.empty())
            .initPlan(
                List.of(
                    PartitionGroupPhase.sequential(
                        Map.of(
                            CurrentClusterConfiguration.DEFAULT_GROUP,
                            List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1))))));

    // when — the manager starts from that pre-planned configuration, then the partition-group
    // appliers are registered afterward (as production does once local partitions are up); that
    // registration is what triggers the continuation, with no external gossip involved
    manager.start(() -> CompletableActorFuture.completed(withPendingPlan)).join();
    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));

    // then — the pending plan is applied and drained
    Awaitility.await("Configuration change is continued after restart")
        .untilAsserted(
            () -> {
              final var defaultGroup =
                  configuration(manager).partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
              assertThat(defaultGroup.hasPendingChanges()).isFalse();
              assertThat(defaultGroup.hasMember(MEMBER_0)).isFalse();
              assertThat(defaultGroup.hasMember(MEMBER_1)).isTrue();
            });
  }

  @Test
  void shouldContinueTwoPhaseJoinOnRestartDuringPromotion() {
    // given — the manager restarts between the two phases of a join: the join operation already
    // completed and marked the local member's partition LEARNER, and the promote operation is
    // still pending. The partition is part of the member's distribution in this state, so on a
    // real broker it is started on boot and the promotion can be driven. The leader's catch-up
    // gate rejects the first promotion attempts, as it does while the learner is not caught up;
    // the reconciler must keep retrying the operation until the gate accepts.
    final var promoteAttempts = new AtomicInteger();
    final PartitionChangeExecutor partitionChangeExecutor = mock(PartitionChangeExecutor.class);
    when(partitionChangeExecutor.promote(1))
        .thenAnswer(
            invocation ->
                promoteAttempts.incrementAndGet() < 3
                    ? CompletableActorFuture.completedExceptionally(
                        new RuntimeException("not caught up yet"))
                    : CompletableActorFuture.completed(null));

    final var persisted =
        PersistedCurrentClusterConfiguration.ofFile(
            tmp.resolve("config-promote-restart.meta"), new ProtoBufSerializer());
    final var manager =
        new ClusterConfigurationManagerImpl(
            executor,
            MEMBER_0,
            persisted,
            new TopologyManagerMetrics(new SimpleMeterRegistry()),
            Duration.ofMillis(1),
            Duration.ofMillis(1));
    manager.setCurrentConfigurationGossiper(ignored -> {});
    manager.registerGlobalChangeAppliers(
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor()));
    final var group =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_0,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.joining(1, partitionConfig).toLearner())),
                MEMBER_1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(2, partitionConfig)))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var withPendingPromotion =
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
                PhasedChangeState.empty())
            .initPlan(
                List.of(
                    PartitionGroupPhase.sequential(
                        Map.of(
                            CurrentClusterConfiguration.DEFAULT_GROUP,
                            List.of(new PartitionPromoteOperation(MEMBER_0, 1))))));

    // when
    manager.start(() -> CompletableActorFuture.completed(withPendingPromotion)).join();
    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        new PartitionGroupConfigurationChangeAppliersImpl(
            partitionChangeExecutor,
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));

    // then — the promotion is retried until the gate accepts and the partition becomes a voting
    // member
    Awaitility.await("Promotion is continued after restart and retried until accepted")
        .untilAsserted(
            () -> {
              final var defaultGroup =
                  configuration(manager).partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
              assertThat(defaultGroup.hasPendingChanges()).isFalse();
              assertThat(defaultGroup.getMember(MEMBER_0).getPartition(1).state())
                  .isEqualTo(PartitionState.State.ACTIVE);
            });
    assertThat(promoteAttempts.get()).isEqualTo(3);
  }

  @Test
  void shouldCompleteAJoinFromBeforeTwoPhaseJoinsAsAVotingMember() {
    // given — a change that was in flight when the broker was upgraded: a join operation created by
    // a version without two-phase joins, so no promote operation follows it. Such an operation
    // must keep its original meaning and end with a voting member, or the partition would be stuck
    // as a learner that nothing promotes.
    final PartitionChangeExecutor partitionChangeExecutor = mock(PartitionChangeExecutor.class);
    when(partitionChangeExecutor.join(anyInt(), any(), any(), eq(false)))
        .thenReturn(CompletableActorFuture.completed(null));

    final var persisted =
        PersistedCurrentClusterConfiguration.ofFile(
            tmp.resolve("config-legacy-join.meta"), new ProtoBufSerializer());
    final var manager =
        new ClusterConfigurationManagerImpl(
            executor,
            MEMBER_0,
            persisted,
            new TopologyManagerMetrics(new SimpleMeterRegistry()),
            Duration.ofMillis(1),
            Duration.ofMillis(1));
    manager.setCurrentConfigurationGossiper(ignored -> {});
    manager.registerGlobalChangeAppliers(
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor()));
    final var group =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(2, partitionConfig)))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var withPendingLegacyJoin =
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
                PhasedChangeState.empty())
            .initPlan(
                List.of(
                    PartitionGroupPhase.sequential(
                        Map.of(
                            CurrentClusterConfiguration.DEFAULT_GROUP,
                            List.of(new PartitionJoinOperation(MEMBER_0, 1, 1, false))))));

    // when
    manager.start(() -> CompletableActorFuture.completed(withPendingLegacyJoin)).join();
    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        new PartitionGroupConfigurationChangeAppliersImpl(
            partitionChangeExecutor,
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));

    // then — the member joined as a voting member in the single step the old operation promised
    Awaitility.await("Legacy join completes with a voting member")
        .untilAsserted(
            () -> {
              final var defaultGroup =
                  configuration(manager).partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
              assertThat(defaultGroup.hasPendingChanges()).isFalse();
              assertThat(defaultGroup.getMember(MEMBER_0).getPartition(1).state())
                  .isEqualTo(PartitionState.State.ACTIVE);
            });
  }

  @Test
  void shouldFinishADrainedChangeWithNoLocalOperationLeftToRun() {
    // given — a change whose every operation was applied by *peers*, merged into one drained plan
    // that nobody has cleared yet. This is the mechanic the queue model never had: finishing a
    // change used to be a side effect of whichever broker applied its last operation, so the broker
    // that finishes it was always one with work of its own. Here the local member has none, so
    // nothing about applying an operation can be what triggers the completion.
    final var persisted =
        PersistedCurrentClusterConfiguration.ofFile(
            tmp.resolve("config-drained-no-local-work.meta"), new ProtoBufSerializer());
    final var manager =
        new ClusterConfigurationManagerImpl(
            executor,
            MEMBER_0,
            persisted,
            new TopologyManagerMetrics(new SimpleMeterRegistry()),
            Duration.ofMillis(1),
            Duration.ofMillis(1));
    manager.setCurrentConfigurationGossiper(ignored -> {});
    manager.registerGlobalChangeAppliers(
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor()));

    // Two independent operations, one per peer, and no operation for MEMBER_0 at all.
    final var graph = OperationGraph.builder();
    final var first = graph.add(new PartitionLeaveOperation(MEMBER_1, 1, 1));
    final var second = graph.add(new PartitionLeaveOperation(MEMBER_2, 2, 1));
    final var started =
        threeMemberCluster()
            .initPlan(
                List.of(
                    new PartitionGroupPhase(
                        Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, graph.build()))));
    // Each peer recorded its own operation and gossiped that; the merge of the two is what the
    // local member holds. Neither peer saw the plan drain, so neither cleared it.
    final var peerOne =
        started.updatePartitionGroupConfig(
            CurrentClusterConfiguration.DEFAULT_GROUP,
            g -> g.completeOperation(first, UnaryOperator.identity()));
    final var peerTwo =
        started.updatePartitionGroupConfig(
            CurrentClusterConfiguration.DEFAULT_GROUP,
            g -> g.completeOperation(second, UnaryOperator.identity()));
    final var drained = peerOne.merge(peerTwo);
    final var drainedGroup = drained.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(drainedGroup.hasPendingChanges()).isFalse();
    assertThat(drainedGroup.pendingChanges()).isPresent();

    // when — the manager starts from that state and the group's appliers are registered, exactly as
    // production does once local partitions are up. There is nothing for the local member to apply.
    manager.start(() -> CompletableActorFuture.completed(drained)).join();
    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));

    // then — the plan is cleared, its completion recorded, and the phased plan archived
    Awaitility.await("Drained change is finished without any local operation")
        .untilAsserted(
            () -> {
              final var config = configuration(manager);
              final var group = config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
              assertThat(group.pendingChanges()).isEmpty();
              assertThat(group.lastChange()).isPresent();
              assertThat(config.phasedChangeState().pending()).isEmpty();
            });
  }

  @Test
  void shouldFinishADrainedChangeOnAGossipRoundThatChangesNothing() {
    // given — the same drained-but-uncleared state, but reached while the manager is already
    // running and with the completion never having been attempted -- start() does not reconcile, so
    // a broker that comes up in this state is waiting for something else to notice. A gossip round
    // that merges to exactly what is already held takes the "merge changed nothing" branch, which
    // skips reconcile() entirely; that branch is the only thing standing between a completion whose
    // persist failed and a change that sits drained forever, since gossip is all that is left to
    // perturb a converged cluster.
    final var manager = newManager(MEMBER_0);
    final var graph = OperationGraph.builder();
    final var peerOperation = graph.add(new PartitionLeaveOperation(MEMBER_1, 1, 1));
    final var drained =
        threeMemberCluster()
            .initPlan(
                List.of(
                    new PartitionGroupPhase(
                        Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, graph.build()))))
            .updatePartitionGroupConfig(
                CurrentClusterConfiguration.DEFAULT_GROUP,
                g -> g.completeOperation(peerOperation, UnaryOperator.identity()));
    manager.start(() -> CompletableActorFuture.completed(drained)).join();
    assertThat(
            configuration(manager)
                .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                .pendingChanges())
        .describedAs("start() alone does not finish the drained change")
        .isPresent();

    // when — a peer gossips the very same state back, so the merge changes nothing locally
    manager.onGossipReceivedCurrent(drained);

    // then
    Awaitility.await("Drained change is finished on a gossip round")
        .untilAsserted(
            () -> {
              final var group =
                  configuration(manager).partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
              assertThat(group.pendingChanges()).isEmpty();
              assertThat(group.lastChange()).isPresent();
            });
  }

  /** Three active members, all replicating partitions 1 and 2 of the default group. */
  private CurrentClusterConfiguration threeMemberCluster() {
    final var replicated =
        BrokerPartitionState.initialize(
            Map.of(
                1, PartitionState.active(1, partitionConfig),
                2, PartitionState.active(1, partitionConfig)));
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(
                MEMBER_0, new BrokerState(0, Instant.EPOCH, State.ACTIVE),
                MEMBER_1, new BrokerState(0, Instant.EPOCH, State.ACTIVE),
                MEMBER_2, new BrokerState(0, Instant.EPOCH, State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()),
        Map.of(
            CurrentClusterConfiguration.DEFAULT_GROUP,
            new PartitionGroupConfiguration(
                1,
                0,
                Map.of(MEMBER_0, replicated, MEMBER_1, replicated, MEMBER_2, replicated),
                Optional.empty(),
                Optional.empty(),
                Optional.empty())),
        PhasedChangeState.empty());
  }

  /**
   * A fresh cluster with no configured cluster id must still come up with one, and it must be
   * visible through the legacy projection.
   */
  @Test
  void shouldStartFromStaticInitializerWithGeneratedClusterId() {
    // given — a fresh manager bootstrapping without a configured cluster id
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
            List.of(new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 1)),
            1,
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, partitionConfig),
            null);

    // when
    manager
        .start(new StaticInitializer<>(staticConfiguration::generateCurrentClusterConfiguration))
        .join();

    // then — the id is generated once and surfaces in the view the Hub ping waits on
    final var config = configuration(manager);
    assertThat(config.globalConfiguration().clusterId()).isPresent();
    assertThat(config.toLegacyDefault().clusterId())
        .isEqualTo(config.globalConfiguration().clusterId());
  }

  private static final class FailingExecutor
      implements ClusterMembershipChangeExecutor, PartitionChangeExecutor {

    private int numFailures;

    private FailingExecutor(final int numFailures) {
      this.numFailures = numFailures;
    }

    @Override
    public ActorFuture<Void> addBroker(final MemberId memberId) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> removeBroker(final MemberId memberId) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> join(
        final int partitionId,
        final Map<MemberId, Integer> membersWithPriority,
        final DynamicPartitionConfig partitionConfig,
        final boolean asLearner) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> leave(final int partitionId) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> promote(final int partitionId) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> demote(final int partitionId) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> bootstrap(
        final int partitionId,
        final int priority,
        final DynamicPartitionConfig partitionConfig,
        final boolean initializeFromConfig) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> reconfigurePriority(final int partitionId, final int newPriority) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> forceReconfigure(
        final int partitionId, final Collection<MemberId> members) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> disableExporter(final int partitionId, final String exporterId) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> deleteExporter(final int partitionId, final String exporterId) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> enableExporter(
        final int partitionId,
        final String exporterId,
        final long metadataVersion,
        final String initializeFrom) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> setExportingState(final ExportingState exportingState) {
      return mayBeFail();
    }

    @Override
    public ActorFuture<Void> deleteHistory() {
      return mayBeFail();
    }

    private ActorFuture<Void> mayBeFail() {
      if (numFailures > 0) {
        numFailures--;
        return TestActorFuture.failedFuture(new RuntimeException("Simulated failure"));
      } else {
        return TestActorFuture.completedFuture(null);
      }
    }
  }
}
