/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.partitioning.PartitionModeHandler.PartitionManagerFactory;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class PartitionModeHandlerTest {

  private static final String GROUP = PartitionManagerImpl.DEFAULT_GROUP_NAME;
  private static final String NON_DEFAULT_GROUP = "tenant-2";
  private static final MemberId LOCAL_MEMBER = MemberId.from("0");

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  private final ControlActor controlActor = new ControlActor();

  private BrokerStartupContext brokerStartupContext;
  private ClusterConfigurationService clusterConfigurationService;
  private TopologyManagerImpl topologyManager;
  private Map<String, PartitionManager> partitionManagers;
  private PartitionManagerImpl normalManager;
  private RecoveryPartitionManager recoveryManager;
  private PartitionManagerFactory partitionManagerFactory;
  private PartitionModeHandler handler;

  @BeforeEach
  void setUp() {
    scheduler.submitActor(controlActor);
    scheduler.workUntilDone();

    clusterConfigurationService = mock(ClusterConfigurationService.class);
    topologyManager = mock(TopologyManagerImpl.class);
    partitionManagers = new HashMap<>();

    normalManager = mock(PartitionManagerImpl.class);
    when(normalManager.start()).thenReturn(CompletableActorFuture.completed(null));
    when(normalManager.stop()).thenReturn(CompletableActorFuture.completed(null));

    recoveryManager = mock(RecoveryPartitionManager.class);
    when(recoveryManager.start()).thenReturn(CompletableActorFuture.completed(null));
    when(recoveryManager.stop()).thenReturn(CompletableActorFuture.completed(null));

    brokerStartupContext = mock(BrokerStartupContext.class);
    when(brokerStartupContext.getConcurrencyControl()).thenReturn(controlActor.getActorControl());
    when(brokerStartupContext.getClusterConfigurationService())
        .thenReturn(clusterConfigurationService);
    when(brokerStartupContext.getPartitionManagers()).thenReturn(partitionManagers);
    // publish the new manager into the resolved map so currentManager()/isRecovering() reflect the
    // transition (the production context does this; a bare mock would leave the map unchanged)
    doAnswer(
            invocation -> {
              partitionManagers.put(invocation.getArgument(0), invocation.getArgument(1));
              return null;
            })
        .when(brokerStartupContext)
        .addPartitionManager(any(), any());

    // local member resolution used by awaitModeApplied to compute the expected partition set
    final var clusterServices = mock(ClusterServicesImpl.class);
    final var membershipService = mock(ClusterMembershipService.class);
    final var localMember = mock(Member.class);
    when(localMember.id()).thenReturn(LOCAL_MEMBER);
    when(membershipService.getLocalMember()).thenReturn(localMember);
    when(clusterServices.getMembershipService()).thenReturn(membershipService);
    when(brokerStartupContext.getClusterServices()).thenReturn(clusterServices);

    partitionManagerFactory = mode -> mode == Mode.RECOVERING ? recoveryManager : normalManager;

    handler = newHandler(GROUP);
  }

  private PartitionModeHandler newHandler(final String group) {
    return new PartitionModeHandler(
        brokerStartupContext, group, topologyManager, partitionManagerFactory);
  }

  private void givenCurrentManager(final String tenantId, final PartitionManager current) {
    partitionManagers.put(tenantId, current);
  }

  /**
   * Stubs the partition distribution so the given partitions are replicated by the local member.
   */
  private void givenLocalPartitions(final Integer... partitionIds) {
    final Set<PartitionMetadata> metadata =
        Arrays.stream(partitionIds)
            .map(
                id ->
                    new PartitionMetadata(
                        new PartitionId(GROUP, id),
                        Set.of(LOCAL_MEMBER),
                        Map.of(LOCAL_MEMBER, 1),
                        1,
                        LOCAL_MEMBER))
            .collect(Collectors.toSet());
    when(clusterConfigurationService.getPartitionDistribution())
        .thenReturn(new PartitionDistribution(metadata));
  }

  private void givenPartitionRoles(final Map<Integer, PartitionRole> roles) {
    when(topologyManager.getLocalPartitionRoles())
        .thenReturn(CompletableActorFuture.completed(Map.copyOf(roles)));
  }

  private void givenPartitionHealth(final Map<Integer, PartitionHealthStatus> health) {
    when(topologyManager.getLocalPartitionHealth())
        .thenReturn(CompletableActorFuture.completed(Map.copyOf(health)));
  }

  private void progress() {
    scheduler.workUntilDone();
  }

  private static final class ControlActor extends Actor {
    ActorControl getActorControl() {
      return actor;
    }
  }

  @Nested
  class EnterRecovery {

    @BeforeEach
    void setUp() {
      givenCurrentManager(GROUP, normalManager);
    }

    @Test
    void shouldCompleteSuccessfully() {
      // when
      final ActorFuture<Void> result = handler.enterRecovery();
      progress();

      // then
      assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldStopCurrentManagerAndStartRecoveryManager() {
      // when
      handler.enterRecovery();
      progress();

      // then
      verify(normalManager).stop();
      verify(recoveryManager).start();
    }

    @Test
    void shouldPublishRecoveryManagerOnContext() {
      // when
      handler.enterRecovery();
      progress();

      // then
      verify(brokerStartupContext).addPartitionManager(GROUP, recoveryManager);
    }

    @Test
    void shouldNotRegisterPartitionExecutors() {
      // when — partition change executors are registered by the manager itself on start()
      handler.enterRecovery();
      progress();

      // then
      verify(clusterConfigurationService, never()).registerPartitionChangeExecutors(any(), any());
    }

    @Test
    void shouldCompleteWithoutAwaitingStart() {
      // given - the recovery manager start stays pending (e.g. waiting on a slow partition
      // shutdown)
      when(recoveryManager.start()).thenReturn(new CompletableActorFuture<>());

      // when
      final ActorFuture<Void> result = handler.enterRecovery();
      progress();

      // then - the operation completes so the cluster change plan can advance to the next member,
      // even though the recovery partitions are not yet fully started
      assertThat(result.isDone()).isTrue();
      assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldCompleteSuccessfullyEvenWhenStartFails() {
      // given
      when(recoveryManager.start())
          .thenReturn(
              CompletableActorFuture.completedExceptionally(new RuntimeException("start failed")));

      // when
      final ActorFuture<Void> result = handler.enterRecovery();
      progress();

      // then - a failed start is logged and handled by health monitoring, not propagated as an
      // operation failure that would stall the cluster change plan
      assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldNoOpWhenAlreadyInRecovery() {
      // given
      givenCurrentManager(GROUP, recoveryManager);

      // when
      handler.enterRecovery();
      progress();

      // then
      verify(recoveryManager, never()).stop();
      verify(recoveryManager, never()).start();
    }

    @Test
    void shouldFailWhenNoManagerPresent() {
      // given
      partitionManagers.remove(GROUP);

      // when
      final ActorFuture<Void> result = handler.enterRecovery();
      progress();

      // then
      assertThat(result.isCompletedExceptionally()).isTrue();
    }
  }

  @Nested
  class ExitRecovery {

    @BeforeEach
    void setUp() {
      givenCurrentManager(GROUP, recoveryManager);
    }

    @Test
    void shouldCompleteSuccessfully() {
      // when
      final ActorFuture<Void> result = handler.exitRecovery();
      progress();

      // then
      assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldStopRecoveryManagerAndStartNormalManager() {
      // when
      handler.exitRecovery();
      progress();

      // then
      verify(recoveryManager).stop();
      verify(normalManager).start();
    }

    @Test
    void shouldNotRegisterPartitionExecutors() {
      // when — partition change executors are registered by the manager itself on start()
      handler.exitRecovery();
      progress();

      // then
      verify(clusterConfigurationService, never()).registerPartitionChangeExecutors(any(), any());
    }

    @Test
    void shouldNoOpWhenAlreadyInNormalMode() {
      // given
      givenCurrentManager(GROUP, normalManager);

      // when
      handler.exitRecovery();
      progress();

      // then
      verify(normalManager, never()).stop();
      verify(normalManager, never()).start();
    }

    @Test
    void shouldPublishNormalManagerOnContext() {
      // when
      handler.exitRecovery();
      progress();

      // then
      verify(brokerStartupContext).addPartitionManager(GROUP, normalManager);
    }

    @Test
    void shouldCompleteWithoutAwaitingStart() {
      // given - the normal manager start stays pending (e.g. waiting for a Raft leader to be
      // elected)
      when(normalManager.start()).thenReturn(new CompletableActorFuture<>());

      // when
      final ActorFuture<Void> result = handler.exitRecovery();
      progress();

      // then - the operation completes so the cluster change plan can advance to the next member,
      // even though the partitions are not yet ready
      assertThat(result.isDone()).isTrue();
      assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldCompleteSuccessfullyEvenWhenStartFails() {
      // given
      when(normalManager.start())
          .thenReturn(
              CompletableActorFuture.completedExceptionally(new RuntimeException("start failed")));

      // when
      final ActorFuture<Void> result = handler.exitRecovery();
      progress();

      // then - a failed start is logged and handled by health monitoring, not propagated as an
      // operation failure that would stall the cluster change plan
      assertThat(result.isCompletedExceptionally()).isFalse();
    }
  }

  @Nested
  class AwaitModeApplied {

    @BeforeEach
    void setUp() {
      // already in processing mode; readiness is verified against the topology partition roles
      givenCurrentManager(GROUP, normalManager);
    }

    @Test
    void shouldFailWhenNotInExpectedMode() {
      // given - the member is in processing mode but a recovery transition is awaited
      // when
      final ActorFuture<Set<Integer>> await = handler.awaitModeApplied(Mode.RECOVERING);
      progress();

      // then
      assertThat(await.isCompletedExceptionally()).isTrue();
    }

    @Test
    void shouldCompleteWithEmptySetWhenNoLocalPartitions() {
      // given - this member replicates no partitions of the group
      givenLocalPartitions();

      // when
      final ActorFuture<Set<Integer>> await = handler.awaitModeApplied(Mode.PROCESSING);
      progress();

      // then
      assertThat(await.isCompletedExceptionally()).isFalse();
      assertThat(await.join()).isEmpty();
    }

    @Test
    void shouldCompleteWithAllPartitionsWhenProcessingRolesReached() {
      // given
      givenLocalPartitions(1, 2);
      givenPartitionRoles(Map.of(1, PartitionRole.LEADER, 2, PartitionRole.FOLLOWER));

      // when
      final ActorFuture<Set<Integer>> await = handler.awaitModeApplied(Mode.PROCESSING);
      progress();

      // then - processing readiness is role-only, no health gating
      assertThat(await.isCompletedExceptionally()).isFalse();
      assertThat(await.join()).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void shouldCompleteWithHealthyPartitionsWhenRecovering() {
      // given
      givenCurrentManager(GROUP, recoveryManager);
      givenLocalPartitions(1, 2);
      givenPartitionRoles(Map.of(1, PartitionRole.INACTIVE, 2, PartitionRole.INACTIVE));
      givenPartitionHealth(
          Map.of(1, PartitionHealthStatus.HEALTHY, 2, PartitionHealthStatus.HEALTHY));

      // when
      final ActorFuture<Set<Integer>> await = handler.awaitModeApplied(Mode.RECOVERING);
      progress();

      // then
      assertThat(await.isCompletedExceptionally()).isFalse();
      assertThat(await.join()).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void shouldExcludeDeadPartitionsFromConfirmedSetButStillComplete() {
      // given - partition 2 reached INACTIVE but never recovered, so it's DEAD
      givenCurrentManager(GROUP, recoveryManager);
      givenLocalPartitions(1, 2);
      givenPartitionRoles(Map.of(1, PartitionRole.INACTIVE, 2, PartitionRole.INACTIVE));
      givenPartitionHealth(Map.of(1, PartitionHealthStatus.HEALTHY, 2, PartitionHealthStatus.DEAD));

      // when
      final ActorFuture<Set<Integer>> await = handler.awaitModeApplied(Mode.RECOVERING);
      progress();

      // then - the operation still completes (a dead partition must not stall the cluster
      // change), but only the healthy partition is in the confirmed set
      assertThat(await.isCompletedExceptionally()).isFalse();
      assertThat(await.join()).containsExactly(1);
    }

    @Test
    void shouldRetryWhenHealthNotYetReported() {
      // given - role reached INACTIVE, but health hasn't been reported yet (a race between
      // RecoveryPartitionManager's role batch and its health batch)
      givenCurrentManager(GROUP, recoveryManager);
      givenLocalPartitions(1);
      givenPartitionRoles(Map.of(1, PartitionRole.INACTIVE));
      givenPartitionHealth(Map.of());

      // when
      final ActorFuture<Set<Integer>> await = handler.awaitModeApplied(Mode.RECOVERING);
      progress();

      // then - the operation fails so the cluster change can be retried, same as an
      // unready role
      assertThat(await.isCompletedExceptionally()).isTrue();
    }

    @Test
    void shouldFailWhenPartitionsNotInExpectedRole() {
      // given - the partition never reaches a processing role
      givenLocalPartitions(1);
      givenPartitionRoles(Map.of(1, PartitionRole.INACTIVE));

      // when
      final ActorFuture<Set<Integer>> await = handler.awaitModeApplied(Mode.PROCESSING);
      progress();

      // then - the operation fails so the cluster change can be retried
      assertThat(await.isCompletedExceptionally()).isTrue();
    }
  }

  @Nested
  class NonDefaultTenant {

    private PartitionModeHandler nonDefaultHandler;

    @BeforeEach
    void setUp() {
      givenCurrentManager(NON_DEFAULT_GROUP, normalManager);
      nonDefaultHandler = newHandler(NON_DEFAULT_GROUP);
    }

    @Test
    void shouldTransitionWithoutRegisteringExecutors() {
      // when
      final ActorFuture<Void> result = nonDefaultHandler.enterRecovery();
      progress();

      // then
      assertThat(result.isCompletedExceptionally()).isFalse();
      verify(recoveryManager).start();
      verify(brokerStartupContext).addPartitionManager(NON_DEFAULT_GROUP, recoveryManager);
      // only the default tenant participates in cluster configuration changes
      verify(clusterConfigurationService, never()).registerPartitionChangeExecutors(any(), any());
    }

    @Test
    void shouldNotRegisterModeExecutorOnRegister() {
      // when
      nonDefaultHandler.register();

      // then
      verify(clusterConfigurationService, never()).registerModeChangeExecutor(any());
    }
  }

  @Nested
  class Register {

    @Test
    void shouldRegisterModeExecutor() {
      // when
      handler.register();

      // then
      verify(clusterConfigurationService).registerModeChangeExecutor(handler);
    }

    @Test
    void shouldNotRegisterPartitionExecutors() {
      // when — partition change executors are owned by the partition manager
      handler.register();

      // then
      verify(clusterConfigurationService, never()).registerPartitionChangeExecutors(any(), any());
    }
  }

  @Nested
  class Close {

    @Test
    void shouldRemoveModeExecutorOnClose() {
      // when
      handler.closeAsync();
      progress();

      // then
      verify(clusterConfigurationService).removeModeChangeExecutor();
    }

    @Test
    void shouldNotRemovePartitionExecutorOnClose() {
      // when — partition change executors are removed by the partition manager on stop()
      handler.closeAsync();
      progress();

      // then
      verify(clusterConfigurationService, never()).removePartitionChangeExecutor();
    }
  }
}
