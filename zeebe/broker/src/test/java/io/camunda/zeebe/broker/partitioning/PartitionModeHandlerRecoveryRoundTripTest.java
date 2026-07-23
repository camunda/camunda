/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.partitioning.PartitionModeHandler.PartitionManagerFactory;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.dynamic.config.changes.AwaitModeChangeApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves the full round trip through recovery mode with a partial failure: one partition fails to
 * recover (reported {@code DEAD}) while its sibling recovers cleanly, the group still confirms and
 * writes {@code RECOVERING} only for the healthy partition, and - crucially - exiting recovery
 * afterward is not blocked or corrupted by that earlier partial failure. The exit path
 * (`PartitionModeHandler#exitRecovery`/`awaitModeApplied(PROCESSING)`) gates purely on Raft role,
 * not health, and starts a brand-new partition manager from scratch, so the group must converge
 * cleanly back to {@code PROCESSING}/{@code ACTIVE} regardless of what happened to partition 2
 * during recovery.
 *
 * <p>Every collaborator is real except the "exit" partition manager: a real Raft-based {@link
 * PartitionManagerImpl} would require a running Raft cluster, so a lightweight fake that marks its
 * local partitions {@code LEADER} on the real {@link TopologyManagerImpl} stands in for it - the
 * same level of fidelity {@link PartitionModeHandlerTest}'s {@code ExitRecovery} tests use with a
 * mocked {@code normalManager}.
 */
final class PartitionModeHandlerRecoveryRoundTripTest {

  private static final String GROUP = PartitionManagerImpl.DEFAULT_GROUP_NAME;
  private static final int PARTITION_ID = 1;
  private static final int PARTITION_ID_2 = 2;
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);

  private ActorScheduler actorScheduler;
  private Actor controlActor;
  private Member localMember;
  private MemberId localMemberId;
  private TopologyManagerImpl topologyManager;
  private PartitionModeHandler handler;

  @BeforeEach
  void setUp() {
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();

    controlActor = new Actor() {};
    actorScheduler.submitActor(controlActor).join();

    localMember = new Member(new MemberConfig());
    localMemberId = localMember.id();

    final var membershipService = mock(ClusterMembershipService.class);
    when(membershipService.getLocalMember()).thenReturn(localMember);

    final var metadata1 = localPartitionMetadata(PARTITION_ID);
    final var metadata2 = localPartitionMetadata(PARTITION_ID_2);
    final var clusterConfigurationService = mock(ClusterConfigurationService.class);
    when(clusterConfigurationService.getPartitionDistribution())
        .thenReturn(new PartitionDistribution(Set.of(metadata1, metadata2)));
    when(clusterConfigurationService.getCurrentClusterConfiguration())
        .thenReturn(ClusterConfiguration.uninitialized());

    final var brokerInfo = new BrokerInfo(0, null, "localhost:26501").setPartitionGroup(GROUP);
    topologyManager = new TopologyManagerImpl(membershipService, brokerInfo);
    actorScheduler.submitActor(topologyManager).join();

    final var transport = mock(AtomixServerTransport.class);
    when(transport.subscribe(any(), any(), any()))
        .thenReturn(CompletableActorFuture.completed(null));
    when(transport.unsubscribe(any(), any())).thenReturn(CompletableActorFuture.completed(null));

    final Map<String, PartitionManager> partitionManagers = new HashMap<>();
    final var initialManager = mock(PartitionManager.class);
    when(initialManager.stop()).thenReturn(CompletableActorFuture.completed(null));
    partitionManagers.put(GROUP, initialManager);

    final var brokerStartupContext = mock(BrokerStartupContext.class);
    when(brokerStartupContext.getConcurrencyControl()).thenReturn(controlActor);
    when(brokerStartupContext.getClusterConfigurationService())
        .thenReturn(clusterConfigurationService);
    when(brokerStartupContext.getPartitionManagers()).thenReturn(partitionManagers);
    // publish the new manager into the resolved map so currentManager()/isRecovering() reflect the
    // transition, mirroring what the production BrokerStartupContext does
    doAnswer(
            invocation -> {
              partitionManagers.put(invocation.getArgument(0), invocation.getArgument(1));
              return null;
            })
        .when(brokerStartupContext)
        .addPartitionManager(any(), any());

    final var clusterServices = mock(ClusterServicesImpl.class);
    when(clusterServices.getMembershipService()).thenReturn(membershipService);
    when(brokerStartupContext.getClusterServices()).thenReturn(clusterServices);

    // recovery manager: real, driven by the real actor scheduler, with partition 2's recovery
    // steps rigged to fail so only partition 1 actually recovers
    final var recoveryManager =
        new RecoveryPartitionManager(
            GROUP,
            new BrokerCfg(),
            brokerInfo,
            controlActor,
            clusterConfigurationService,
            membershipService,
            new FailingActorSchedulingService(actorScheduler, PARTITION_ID_2),
            new SimpleMeterRegistry(),
            transport,
            (ignored) -> 0L,
            topologyManager);

    // exit manager: a lightweight fake standing in for a real Raft-based PartitionManagerImpl -
    // it just marks both local partitions LEADER on start, matching what PartitionModeHandlerTest
    // does with a mocked normalManager
    final var exitManager =
        new LeaderPartitionManager(controlActor, topologyManager, PARTITION_ID, PARTITION_ID_2);

    final PartitionManagerFactory partitionManagerFactory =
        mode -> mode == Mode.RECOVERING ? recoveryManager : exitManager;

    handler =
        new PartitionModeHandler(
            brokerStartupContext, GROUP, topologyManager, partitionManagerFactory);
  }

  @AfterEach
  void tearDown() {
    if (controlActor != null) {
      controlActor.closeAsync().join();
    }
    actorScheduler.stop();
  }

  private PartitionMetadata localPartitionMetadata(final int partitionId) {
    return new PartitionMetadata(
        new PartitionId(GROUP, partitionId),
        Set.of(localMemberId),
        Map.of(localMemberId, 1),
        1,
        localMemberId);
  }

  @Test
  void shouldConvergeToProcessingAfterRecoveryWithPartialFailure() {
    // given - both partitions start out ACTIVE in cluster configuration
    final var partitionConfig = DynamicPartitionConfig.init();
    var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(
                localMemberId,
                MemberState.initializeAsActive(
                    Map.of(
                        PARTITION_ID, PartitionState.active(1, partitionConfig),
                        PARTITION_ID_2, PartitionState.active(1, partitionConfig))));

    // when - entering recovery, partition 2's recovery steps fail to schedule
    final var enterResult = handler.enterRecovery();

    // then - the operation completes without waiting for the recovery manager to fully start
    assertThat(enterResult).succeedsWithin(AWAIT_TIMEOUT);

    // and - partition 1 recovers healthily, partition 2 reaches INACTIVE but never recovers so
    // it is reported DEAD
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .allSatisfy(
                      info -> {
                        assertThat(info.getPartitionRoles())
                            .containsEntry(PARTITION_ID, PartitionRole.INACTIVE)
                            .containsEntry(PARTITION_ID_2, PartitionRole.INACTIVE);
                        assertThat(info.getPartitionHealthStatuses())
                            .containsEntry(PARTITION_ID, PartitionHealthStatus.HEALTHY)
                            .containsEntry(PARTITION_ID_2, PartitionHealthStatus.DEAD);
                      });
            });

    // and - confirming and writing recovery state excludes the dead partition but still completes
    final var recoveringConfirmed = handler.awaitModeApplied(Mode.RECOVERING);
    assertThat(recoveringConfirmed).succeedsWithin(AWAIT_TIMEOUT);
    assertThat(recoveringConfirmed.join()).containsExactly(PARTITION_ID);

    final var recoveringApplier =
        new AwaitModeChangeApplier(localMemberId, Mode.RECOVERING, handler);
    final var recoveringApplyResult = recoveringApplier.apply();
    assertThat(recoveringApplyResult).succeedsWithin(AWAIT_TIMEOUT);
    clusterConfiguration = recoveringApplyResult.join().apply(clusterConfiguration);

    assertThat(clusterConfiguration.getMember(localMemberId).getPartition(PARTITION_ID).state())
        .isEqualTo(PartitionState.State.RECOVERING);
    assertThat(clusterConfiguration.getMember(localMemberId).getPartition(PARTITION_ID_2).state())
        .isEqualTo(PartitionState.State.ACTIVE);

    // and - exiting recovery replaces the recovery manager (including its partial failure) with a
    // brand-new partition manager
    final var exitResult = handler.exitRecovery();
    assertThat(exitResult).succeedsWithin(AWAIT_TIMEOUT);

    // and - the new manager brings both partitions to a processing role, including the one that
    // was DEAD during recovery
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionRoles())
                              .containsEntry(PARTITION_ID, PartitionRole.LEADER)
                              .containsEntry(PARTITION_ID_2, PartitionRole.LEADER));
            });

    // and - confirming exit is role-only: partition 2's earlier DEAD health status must not matter
    final var processingConfirmed = handler.awaitModeApplied(Mode.PROCESSING);
    assertThat(processingConfirmed).succeedsWithin(AWAIT_TIMEOUT);
    assertThat(processingConfirmed.join()).containsExactlyInAnyOrder(PARTITION_ID, PARTITION_ID_2);

    final var processingApplier =
        new AwaitModeChangeApplier(localMemberId, Mode.PROCESSING, handler);
    final var processingApplyResult = processingApplier.apply();
    assertThat(processingApplyResult).succeedsWithin(AWAIT_TIMEOUT);
    clusterConfiguration = processingApplyResult.join().apply(clusterConfiguration);

    // then - the whole group converges cleanly back to ACTIVE, unaffected by the earlier partial
    // recovery failure
    assertThat(clusterConfiguration.getMember(localMemberId).getPartition(PARTITION_ID).state())
        .isEqualTo(PartitionState.State.ACTIVE);
    assertThat(clusterConfiguration.getMember(localMemberId).getPartition(PARTITION_ID_2).state())
        .isEqualTo(PartitionState.State.ACTIVE);
  }

  /** Fails scheduling of actors for the given partition id(s), leaving others unaffected. */
  private static final class FailingActorSchedulingService implements ActorSchedulingService {
    private final ActorSchedulingService delegate;
    private final Set<Integer> failingPartitionIds;

    private FailingActorSchedulingService(
        final ActorSchedulingService delegate, final Integer... failingPartitionIds) {
      this.delegate = delegate;
      this.failingPartitionIds = Set.of(failingPartitionIds);
    }

    @Override
    public ActorFuture<Void> submitActor(final Actor actor) {
      return shouldFail(actor) ? failure() : delegate.submitActor(actor);
    }

    @Override
    public ActorFuture<Void> submitActor(final Actor actor, final SchedulingHints schedulingHints) {
      return shouldFail(actor) ? failure() : delegate.submitActor(actor, schedulingHints);
    }

    private boolean shouldFail(final Actor actor) {
      return failingPartitionIds.stream().anyMatch(id -> actor.getName().endsWith("-" + id));
    }

    private ActorFuture<Void> failure() {
      return CompletableActorFuture.completedExceptionally(
          new RuntimeException("Injected failure for partition(s) " + failingPartitionIds));
    }
  }

  /**
   * Stands in for a real Raft-based {@link PartitionManagerImpl}: on {@link #start()} it marks all
   * of its given partitions {@code LEADER} on the real {@link TopologyManagerImpl}, then completes
   * - the same level of fidelity {@link PartitionModeHandlerTest}'s {@code ExitRecovery} tests use
   * with a mocked {@code normalManager}.
   */
  private static final class LeaderPartitionManager implements PartitionManager {
    private final ConcurrencyControl concurrencyControl;
    private final TopologyManagerImpl topologyManager;
    private final int[] partitionIds;

    private LeaderPartitionManager(
        final ConcurrencyControl concurrencyControl,
        final TopologyManagerImpl topologyManager,
        final int... partitionIds) {
      this.concurrencyControl = concurrencyControl;
      this.topologyManager = topologyManager;
      this.partitionIds = partitionIds;
    }

    @Override
    public RaftPartition getRaftPartition(final int partitionId) {
      return null;
    }

    @Override
    public Collection<RaftPartition> getRaftPartitions() {
      return List.of();
    }

    @Override
    public Collection<ZeebePartition> getZeebePartitions() {
      return List.of();
    }

    @Override
    public ActorFuture<Void> start() {
      final var result = concurrencyControl.<Void>createFuture();
      final var leaderFutures =
          Arrays.stream(partitionIds).mapToObj(id -> topologyManager.setLeader(1, id)).toList();
      concurrencyControl.runOnCompletion(
          leaderFutures,
          error -> {
            if (error != null) {
              result.completeExceptionally(error);
            } else {
              result.complete(null);
            }
          });
      return result;
    }

    @Override
    public ActorFuture<Void> stop() {
      return CompletableActorFuture.completed(null);
    }
  }
}
