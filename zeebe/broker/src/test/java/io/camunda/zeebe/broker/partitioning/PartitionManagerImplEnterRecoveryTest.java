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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.PartitionRaftListener;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.transport.snapshotapi.SnapshotApiRequestHandler;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

final class PartitionManagerImplEnterRecoveryTest {

  private static final String GROUP = PartitionManagerImpl.DEFAULT_GROUP_NAME;

  private ActorScheduler actorScheduler;
  private Actor controlActor;
  private ClusterConfigurationService clusterConfigurationService;
  private PartitionManagerImpl partitionManager;
  private RecoveryPartitionManager mockRecoveryManager;

  @BeforeEach
  void setUp() {
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();

    controlActor = new Actor() {};
    actorScheduler.submitActor(controlActor).join();

    final var localMember = new Member(new MemberConfig());
    final var membershipService = mock(ClusterMembershipService.class);
    when(membershipService.getLocalMember()).thenReturn(localMember);

    final var clusterServices = mock(ClusterServices.class, Answers.RETURNS_DEEP_STUBS);
    when(clusterServices.getMembershipService()).thenReturn(membershipService);

    clusterConfigurationService = mock(ClusterConfigurationService.class);
    when(clusterConfigurationService.getPartitionDistribution())
        .thenReturn(PartitionDistribution.NO_PARTITIONS);

    final var brokerInfo = new BrokerInfo(0, null, "localhost:26501").setPartitionGroup(GROUP);
    final var topologyManager = new TopologyManagerImpl(membershipService, brokerInfo);
    actorScheduler.submitActor(topologyManager).join();

    final var brokerCfg = new BrokerCfg();

    partitionManager =
        new PartitionManagerImpl(
            GROUP,
            controlActor,
            actorScheduler,
            brokerCfg,
            brokerInfo,
            clusterServices,
            mock(BrokerHealthCheckService.class),
            mock(DiskSpaceUsageMonitor.class),
            List.of(mock(PartitionListener.class)),
            List.of(mock(PartitionRaftListener.class)),
            mock(SnapshotApiRequestHandler.class),
            mock(ExporterRepository.class),
            mock(AtomixServerTransport.class),
            mock(JobStreamer.class),
            clusterConfigurationService,
            new SimpleMeterRegistry(),
            mock(BrokerClient.class),
            null,
            mock(EngineSecurityConfig.class),
            mock(SearchClientsProxy.class),
            mock(BrokerRequestAuthorizationConverter.class),
            topologyManager);

    mockRecoveryManager = mock(RecoveryPartitionManager.class);
    when(mockRecoveryManager.transition()).thenReturn(CompletableActorFuture.completed(null));
    partitionManager.transitionFactory((tenantId, tm) -> mockRecoveryManager);
    partitionManager.postTransition(pm -> {});
  }

  @AfterEach
  void tearDown() {
    controlActor.closeAsync().join();
    actorScheduler.stop();
  }

  @Nested
  class EnterRecovery {

    private AtomicReference<ActorFuture<Void>> enterRecoveryFuture;

    @BeforeEach
    void setUp() {
      enterRecoveryFuture = new AtomicReference<>();
    }

    @Test
    void shouldCompleteSuccessfully() {
      // when
      controlActor.run(() -> enterRecoveryFuture.set(partitionManager.enterRecovery()));

      // then
      awaitEnterRecovery();
      assertThat(enterRecoveryFuture.get().isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldInvokeTransitionFactoryWithPartitionGroupAndTopologyManager() {
      // given
      final var capturedTenantId = new AtomicReference<String>();
      partitionManager.transitionFactory(
          (tenantId, tm) -> {
            capturedTenantId.set(tenantId);
            return mockRecoveryManager;
          });

      // when
      controlActor.run(() -> enterRecoveryFuture.set(partitionManager.enterRecovery()));
      awaitEnterRecovery();

      // then
      assertThat(capturedTenantId.get()).isEqualTo(GROUP);
    }

    @Test
    void shouldCallTransitionOnRecoveryManagerFromFactory() {
      // when
      controlActor.run(() -> enterRecoveryFuture.set(partitionManager.enterRecovery()));
      awaitEnterRecovery();

      // then
      verify(mockRecoveryManager).transition();
    }

    @Test
    void shouldRemovePartitionChangeExecutorAfterSuccessfulTransition() {
      // when
      controlActor.run(() -> enterRecoveryFuture.set(partitionManager.enterRecovery()));
      awaitEnterRecovery();

      // then
      verify(clusterConfigurationService).removePartitionChangeExecutor();
    }

    @Test
    void shouldInvokePostTransitionWithNewRecoveryManager() {
      // given
      final var capturedManager = new AtomicReference<PartitionManager>();
      partitionManager.postTransition(capturedManager::set);

      // when
      controlActor.run(() -> enterRecoveryFuture.set(partitionManager.enterRecovery()));
      awaitEnterRecovery();

      // then
      assertThat(capturedManager.get()).isSameAs(mockRecoveryManager);
    }

    @Test
    void shouldFailWhenRecoveryManagerTransitionFails() {
      // given
      when(mockRecoveryManager.transition())
          .thenReturn(
              CompletableActorFuture.completedExceptionally(
                  new RuntimeException("transition failed")));

      // when
      controlActor.run(() -> enterRecoveryFuture.set(partitionManager.enterRecovery()));
      awaitEnterRecovery();

      // then
      assertThat(enterRecoveryFuture.get().isCompletedExceptionally()).isTrue();
    }

    @Test
    void shouldNotRemovePartitionChangeExecutorOnTransitionFailure() {
      // given
      when(mockRecoveryManager.transition())
          .thenReturn(
              CompletableActorFuture.completedExceptionally(
                  new RuntimeException("transition failed")));

      // when
      controlActor.run(() -> enterRecoveryFuture.set(partitionManager.enterRecovery()));
      awaitEnterRecovery();

      // then
      verify(clusterConfigurationService, never()).removePartitionChangeExecutor();
    }

    @Test
    void shouldNotInvokePostTransitionOnTransitionFailure() {
      // given
      when(mockRecoveryManager.transition())
          .thenReturn(
              CompletableActorFuture.completedExceptionally(
                  new RuntimeException("transition failed")));
      final var callbackInvoked = new AtomicBoolean(false);
      partitionManager.postTransition(pm -> callbackInvoked.set(true));

      // when
      controlActor.run(() -> enterRecoveryFuture.set(partitionManager.enterRecovery()));
      awaitEnterRecovery();

      // then
      assertThat(callbackInvoked).isFalse();
    }

    private void awaitEnterRecovery() {
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> enterRecoveryFuture.get() != null && enterRecoveryFuture.get().isDone());
    }
  }
}
