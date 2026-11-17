/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.rebalance.RebalanceCoordinator;
import io.camunda.zeebe.rebalance.RebalanceOverrides;
import io.camunda.zeebe.rebalance.TriggerRebalanceRequest;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RebalanceCoordinatorStepTest {

  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  private final TestConcurrencyControl spyConcurrencyControl = spy(new TestConcurrencyControl());
  private final ClusterConfigurationService clusterConfigurationService =
      mock(ClusterConfigurationService.class);

  private MockBrokerStartupContext testBrokerStartupContext;
  private ClusterCommunicationService communicationService;

  private final RebalanceCoordinatorStep sut = new RebalanceCoordinatorStep();

  @BeforeEach
  void setUp() {
    actorSchedulerRule.before();

    testBrokerStartupContext = new MockBrokerStartupContext();
    testBrokerStartupContext.setBrokerConfiguration(new BrokerCfg());
    testBrokerStartupContext.setConcurrencyControl(spyConcurrencyControl);
    testBrokerStartupContext.setActorSchedulingService(actorSchedulerRule.get());
    testBrokerStartupContext.setClusterConfigurationService(clusterConfigurationService);

    communicationService = testBrokerStartupContext.getClusterServices().getCommunicationService();
  }

  @AfterEach
  void tearDown() {
    actorSchedulerRule.after();
  }

  @Test
  void shouldHaveDescriptiveName() {
    // when
    final var actual = sut.getName();

    // then
    assertThat(actual).isSameAs("Rebalance Coordinator");
  }

  @Nested
  class StartupBehavior {

    @Test
    void shouldWaitForActorSubmission() {
      // given
      final var actorSchedulingService = mock(ActorSchedulingService.class);
      final ActorFuture<Void> submitActorFuture = new CompletableActorFuture<>();
      when(actorSchedulingService.submitActor(any())).thenReturn(submitActorFuture);
      testBrokerStartupContext.setActorSchedulingService(actorSchedulingService);

      // when
      final var startupFuture = sut.startup(testBrokerStartupContext);

      // then
      assertThat(startupFuture.isDone()).isFalse();
      verify(clusterConfigurationService, never())
          .addUpdateListener(any(RebalanceCoordinator.class));

      // when
      submitActorFuture.complete(null);

      // then
      assertThat(startupFuture).succeedsWithin(TIME_OUT);
      verify(clusterConfigurationService, timeout(TIME_OUT.toMillis()))
          .addUpdateListener(any(RebalanceCoordinator.class));
    }

    @Test
    void shouldFailStartupWhenActorSubmissionFails() {
      // given
      final var actorSchedulingService = mock(ActorSchedulingService.class);
      final var expectedError = new RuntimeException("expected failure");
      final ActorFuture<Void> submitActorFuture = new CompletableActorFuture<>();
      submitActorFuture.completeExceptionally(expectedError);
      when(actorSchedulingService.submitActor(any())).thenReturn(submitActorFuture);
      testBrokerStartupContext.setActorSchedulingService(actorSchedulingService);

      // when
      final var startupFuture = sut.startup(testBrokerStartupContext);

      // then
      assertThat(startupFuture).failsWithin(TIME_OUT);
      verify(clusterConfigurationService, never())
          .addUpdateListener(any(RebalanceCoordinator.class));
      verifyNoInteractions(communicationService);
    }

    @Test
    void shouldRegisterHandlersAndListenerOnSuccessfulStartup() {
      // when
      final var startupFuture = sut.startup(testBrokerStartupContext);

      // then
      assertThat(startupFuture).succeedsWithin(TIME_OUT);
      verify(clusterConfigurationService, timeout(TIME_OUT.toMillis()))
          .addUpdateListener(any(RebalanceCoordinator.class));
      verify(communicationService, timeout(TIME_OUT.toMillis()).times(3))
          .replyTo(any(), any(), any(), any());
    }
  }

  @Nested
  class ShutdownBehavior {

    @Test
    void shouldBeSafeAfterPartialStartup() {
      // when
      final var shutdownFuture = sut.shutdown(testBrokerStartupContext);

      // then
      assertThat(shutdownFuture).succeedsWithin(TIME_OUT);
      verifyNoInteractions(clusterConfigurationService);
      verifyNoInteractions(communicationService);
    }

    @Test
    void shouldCloseHandlersRemoveListenerAbandonCoordinatorAndCloseActor() {
      // given
      final var startupFuture = sut.startup(testBrokerStartupContext);
      assertThat(startupFuture).succeedsWithin(TIME_OUT);

      final ArgumentCaptor<RebalanceCoordinator> coordinatorCaptor =
          ArgumentCaptor.forClass(RebalanceCoordinator.class);
      verify(clusterConfigurationService, timeout(TIME_OUT.toMillis()))
          .addUpdateListener(coordinatorCaptor.capture());
      final var registeredCoordinator = coordinatorCaptor.getValue();

      // when
      final var shutdownFuture = sut.shutdown(testBrokerStartupContext);

      // then
      assertThat(shutdownFuture).succeedsWithin(TIME_OUT);
      verify(communicationService, times(3)).unsubscribe(any());
      verify(clusterConfigurationService).removeUpdateListener(registeredCoordinator);
    }

    @Test
    void shouldCompleteWithoutWaitingForAbandonedRunner() {
      // given
      final var startupFuture = sut.startup(testBrokerStartupContext);
      assertThat(startupFuture).succeedsWithin(TIME_OUT);

      // when
      final var shutdownFuture = sut.shutdown(testBrokerStartupContext);

      // then
      assertThat(shutdownFuture).succeedsWithin(Duration.ofSeconds(2));
    }

    @Test
    void shouldBeSafeToShutdownTwice() {
      // given
      final var startupFuture = sut.startup(testBrokerStartupContext);
      assertThat(startupFuture).succeedsWithin(TIME_OUT);
      assertThat(sut.shutdown(testBrokerStartupContext)).succeedsWithin(TIME_OUT);

      // when
      final var secondShutdownFuture = sut.shutdown(testBrokerStartupContext);

      // then
      assertThat(secondShutdownFuture).succeedsWithin(TIME_OUT);
    }

    @Test
    void shouldNotInitiateTheNextTransferAfterShutdownDuringAnInFlightTransfer() {
      // given
      final var member1 = MemberId.from(1);
      final var member2 = MemberId.from(2);
      when(testBrokerStartupContext
              .getClusterServices()
              .getMembershipService()
              .getLocalMember()
              .id())
          .thenReturn(member1);
      final var topology = mock(BrokerClusterState.class);
      when(topology.getLeaderForPartition(1)).thenReturn(new BrokerMemberId(member1));
      when(topology.getLeaderForPartition(2)).thenReturn(new BrokerMemberId(member1));
      final var topologyManager = mock(BrokerTopologyManager.class);
      when(topologyManager.getTopology(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
          .thenReturn(topology);
      final var brokerClient = mock(BrokerClient.class);
      when(brokerClient.getTopologyManager()).thenReturn(topologyManager);
      testBrokerStartupContext.setBrokerClient(brokerClient);
      when(testBrokerStartupContext.getRequestIdGenerator().nextId()).thenReturn(1L);

      final var startupFuture = sut.startup(testBrokerStartupContext);
      assertThat(startupFuture).succeedsWithin(TIME_OUT);

      final ArgumentCaptor<RebalanceCoordinator> coordinatorCaptor =
          ArgumentCaptor.forClass(RebalanceCoordinator.class);
      verify(clusterConfigurationService, timeout(TIME_OUT.toMillis()))
          .addUpdateListener(coordinatorCaptor.capture());
      final var registeredCoordinator = coordinatorCaptor.getValue();

      registeredCoordinator.onClusterConfigurationUpdated(
          twoPartitionsForTransfer(member1, member2));
      assertThat(
              registeredCoordinator.triggerRebalance(
                  new TriggerRebalanceRequest(RebalanceOverrides.none(), false)))
          .succeedsWithin(TIME_OUT);

      // the first partition's transfer is in flight; capture the handler its leader would use to
      // report the outcome back
      verify(communicationService, timeout(TIME_OUT.toMillis()))
          .send(any(), any(), any(), any(), eq(member1), any());
      final ArgumentCaptor<Function> resultHandlerCaptor = ArgumentCaptor.forClass(Function.class);
      verify(communicationService, timeout(TIME_OUT.toMillis()).times(4))
          .replyTo(any(), any(), resultHandlerCaptor.capture(), any());
      @SuppressWarnings("unchecked")
      final Function<
              LeadershipTransferResultRequest, CompletableFuture<LeadershipTransferResultResponse>>
          firstTransferResultHandler = resultHandlerCaptor.getAllValues().get(3);

      // when
      final var shutdownFuture = sut.shutdown(testBrokerStartupContext);
      assertThat(shutdownFuture).succeedsWithin(TIME_OUT);
      firstTransferResultHandler.apply(
          LeadershipTransferResultRequest.builder()
              .withLeader(member1)
              .withDesiredLeader(member2)
              .withResult(LeadershipTransferResult.TRANSFERRED)
              .withCorrelationId(1)
              .build());

      // then
      verify(communicationService, after(500).times(1))
          .send(any(), any(), any(), any(), any(), any());
    }

    private CurrentClusterConfiguration twoPartitionsForTransfer(
        final MemberId from, final MemberId to) {
      final var partitionConfig = DynamicPartitionConfig.init();
      final Map<Integer, PartitionState> partitions =
          Map.of(
              1,
              PartitionState.active(1, partitionConfig),
              2,
              PartitionState.active(1, partitionConfig));
      final Map<Integer, PartitionState> desiredPartitions =
          Map.of(
              1,
              PartitionState.active(2, partitionConfig),
              2,
              PartitionState.active(2, partitionConfig));
      final var groupMembers =
          Map.of(
              from, BrokerPartitionState.initialize(partitions),
              to, BrokerPartitionState.initialize(desiredPartitions));
      final Map<MemberId, BrokerState> globalMembers =
          Map.of(from, BrokerState.initializeAsActive(), to, BrokerState.initializeAsActive());
      final var group =
          new PartitionGroupConfiguration(
              PartitionGroupConfiguration.INITIAL_VERSION,
              PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
              groupMembers,
              Optional.empty(),
              Optional.empty(),
              Optional.empty());
      final var globalConfiguration =
          new GlobalConfiguration(
              GlobalConfiguration.INITIAL_VERSION,
              Optional.empty(),
              globalMembers,
              Optional.empty(),
              Optional.empty(),
              Optional.empty());
      return new CurrentClusterConfiguration(
          CurrentClusterConfiguration.INITIAL_VERSION,
          globalConfiguration,
          Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, group),
          PhasedChangeState.empty());
    }
  }
}
