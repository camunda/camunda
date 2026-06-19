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
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RecoveryPartitionManagerTest {

  private static final String GROUP = PartitionManagerImpl.DEFAULT_GROUP_NAME;
  private static final int PARTITION_ID = 1;
  private static final int PARTITION_ID_2 = 2;

  @TempDir private Path dataDirectory;

  private ActorScheduler actorScheduler;
  private Actor controlActor;
  private Member localMember;
  private MemberId localMemberId;
  private ClusterConfigurationService clusterConfigurationService;
  private ClusterServices clusterServices;
  private RecoveryPartitionManager partitionManager;
  private TopologyManagerImpl topologyManager;

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

    clusterServices = mock(ClusterServices.class);
    when(clusterServices.getMembershipService()).thenReturn(membershipService);

    final var metadata = localPartitionMetadata(PARTITION_ID);
    final var metadata2 = localPartitionMetadata(PARTITION_ID_2);
    clusterConfigurationService = mock(ClusterConfigurationService.class);
    when(clusterConfigurationService.getPartitionDistribution())
        .thenReturn(new PartitionDistribution(Set.of(metadata, metadata2)));

    final var brokerInfo = new BrokerInfo(0, null, "localhost:26501").setPartitionGroup(GROUP);
    topologyManager = new TopologyManagerImpl(membershipService, brokerInfo);
    actorScheduler.submitActor(topologyManager).join();

    partitionManager =
        new RecoveryPartitionManager(
            GROUP,
            dataDirectory.toString(),
            controlActor,
            clusterConfigurationService,
            clusterServices.getMembershipService(),
            actorScheduler,
            new SimpleMeterRegistry(),
            topologyManager);
  }

  private PartitionMetadata localPartitionMetadata(final int partitionId) {
    return new PartitionMetadata(
        PartitionId.from(GROUP, partitionId),
        Set.of(localMemberId),
        Map.of(localMemberId, 1),
        1,
        localMemberId);
  }

  @AfterEach
  void tearDown() {
    if (partitionManager != null) {
      partitionManager.stop().join();
    }
    if (controlActor != null) {
      controlActor.closeAsync().join();
    }
    actorScheduler.stop();
  }

  @Test
  void shouldDeactivateLocalPartitionsOnStart() {
    // when
    controlActor.run(() -> partitionManager.start());

    // then
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionRoles())
                              .containsEntry(PARTITION_ID, PartitionRole.INACTIVE)
                              .containsEntry(PARTITION_ID_2, PartitionRole.INACTIVE));
            });
  }

  @Test
  void shouldNotStartRaftOrZeebePartitions() {
    // when
    controlActor.run(() -> partitionManager.start());

    // then
    assertThat(partitionManager.getRaftPartitions()).isEmpty();
    assertThat(partitionManager.getZeebePartitions()).isEmpty();
    assertThat(partitionManager.getRaftPartition(PARTITION_ID)).isNull();
  }

  @Nested
  class TransitionToRecovery {

    private AtomicReference<ActorFuture<Void>> transitionFuture;

    @BeforeEach
    void setUp() {
      transitionFuture = new AtomicReference<>();
    }

    @Test
    void shouldCompleteTransitionFutureOnSuccess() {
      // when
      controlActor.run(() -> transitionFuture.set(partitionManager.transition()));

      // then
      awaitTransition();
      assertThat(transitionFuture.get().isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldDeactivateLocalPartitionsOnTransition() {
      // when
      controlActor.run(() -> transitionFuture.set(partitionManager.transition()));
      awaitTransition();

      // then
      await()
          .untilAsserted(
              () -> {
                final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
                assertThat(publishedInfos)
                    .anySatisfy(
                        info ->
                            assertThat(info.getPartitionRoles())
                                .containsEntry(PARTITION_ID, PartitionRole.INACTIVE)
                                .containsEntry(PARTITION_ID_2, PartitionRole.INACTIVE));
              });
    }

    @Test
    void shouldCompleteImmediatelyWhenNoLocalPartitions() {
      // given
      when(clusterConfigurationService.getPartitionDistribution())
          .thenReturn(new PartitionDistribution(Set.of()));
      final var callbackInvoked = new AtomicBoolean(false);
      partitionManager.postTransition(pm -> callbackInvoked.set(true));

      // when
      controlActor.run(() -> transitionFuture.set(partitionManager.transition()));

      // then
      await()
          .atMost(Duration.ofSeconds(1))
          .until(() -> transitionFuture.get() != null && transitionFuture.get().isDone());
      assertThat(transitionFuture.get().isCompletedExceptionally()).isFalse();
      assertThat(callbackInvoked).isFalse();
    }

    @Test
    void shouldFailTransitionWhenDeactivationFails() {
      // given
      topologyManager.closeAsync().join();

      // when
      controlActor.run(() -> transitionFuture.set(partitionManager.transition()));

      // then
      awaitTransition();
      assertThat(transitionFuture.get().isCompletedExceptionally()).isTrue();
    }

    @Test
    void shouldNotInvokePostTransitionOnDeactivationFailure() {
      // given
      topologyManager.closeAsync().join();
      final var callbackInvoked = new AtomicBoolean(false);
      partitionManager.postTransition(pm -> callbackInvoked.set(true));

      // when
      controlActor.run(() -> transitionFuture.set(partitionManager.transition()));
      awaitTransition();

      // then
      assertThat(callbackInvoked).isFalse();
    }

    @Test
    void shouldStopCleanlyAfterDeactivationFailure() {
      // given
      topologyManager.closeAsync().join();

      // when
      controlActor.run(() -> transitionFuture.set(partitionManager.transition()));
      awaitTransition();

      // then
      assertThat(transitionFuture.get().isCompletedExceptionally()).isTrue();
      assertThat(partitionManager.stop()).succeedsWithin(Duration.ofSeconds(5));
    }

    private void awaitTransition() {
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> transitionFuture.get() != null && transitionFuture.get().isDone());
    }
  }
}
