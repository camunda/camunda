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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreType;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
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

  private ActorScheduler actorScheduler;
  private Actor controlActor;
  private Member localMember;
  private MemberId localMemberId;
  private ClusterConfigurationService clusterConfigurationService;
  private ClusterServices clusterServices;
  private RecoveryPartitionManager partitionManager;
  private TopologyManagerImpl topologyManager;
  private BrokerInfo brokerInfo;
  private AtomixServerTransport transport;

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

    brokerInfo = new BrokerInfo(0, null, "localhost:26501").setPartitionGroup(GROUP);
    topologyManager = new TopologyManagerImpl(membershipService, brokerInfo);
    actorScheduler.submitActor(topologyManager).join();

    transport = mock(AtomixServerTransport.class);
    when(transport.subscribe(any(), any(), any()))
        .thenReturn(CompletableActorFuture.completed(null));
    when(transport.unsubscribe(any(), any())).thenReturn(CompletableActorFuture.completed(null));

    partitionManager = buildManager(new BrokerCfg(), actorScheduler);
  }

  private RecoveryPartitionManager buildManager(
      final BrokerCfg brokerCfg, final ActorSchedulingService schedulingService) {
    return new RecoveryPartitionManager(
        GROUP,
        brokerCfg,
        brokerInfo,
        controlActor,
        clusterConfigurationService,
        clusterServices.getMembershipService(),
        schedulingService,
        new SimpleMeterRegistry(),
        transport,
        null,
        topologyManager);
  }

  private PartitionMetadata localPartitionMetadata(final int partitionId) {
    return new PartitionMetadata(
        new PartitionId(GROUP, partitionId),
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

  @Test
  void shouldCreateAndCloseBackupStoreAcrossRepeatedStartStopCycles(@TempDir final Path tempDir) {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().getBackup().setStore(BackupStoreType.FILESYSTEM);
    brokerCfg.getData().getBackup().getFilesystem().setBasePath(tempDir.toString());
    partitionManager = buildManager(brokerCfg, actorScheduler);

    // when/then: each cycle must create a fresh backup store and fully close the previous one;
    // a leaked or half-closed store would make the next start()/stop() hang or fail
    for (int i = 0; i < 2; i++) {
      assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));
      assertThat(partitionManager.stop()).succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldToleratePartialStartFailureAndStillDeactivateAllLocalPartitions() {
    // given: partition 2's recovery steps fail to schedule, so only partition 1 recovers
    partitionManager =
        buildManager(
            new BrokerCfg(), new FailingActorSchedulingService(actorScheduler, PARTITION_ID_2));

    // when
    assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));

    // then: start() still succeeds overall, but both partitions - including the one that
    // failed to start - are marked INACTIVE so nothing assumes partition 2 is serving traffic
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

    // and: only the partition that failed to start is reported as DEAD, since it never
    // recovered and nothing is left running to ever bring it back; the one that succeeded is
    // reported HEALTHY
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionHealthStatuses())
                              .containsEntry(PARTITION_ID, PartitionHealthStatus.HEALTHY)
                              .containsEntry(PARTITION_ID_2, PartitionHealthStatus.DEAD));
            });
  }

  @Test
  void shouldReportHealthyForSuccessfullyRecoveredPartitions() {
    // when
    controlActor.run(() -> partitionManager.start());

    // then - both partitions started successfully, so both are reported healthy
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionHealthStatuses())
                              .containsEntry(PARTITION_ID, PartitionHealthStatus.HEALTHY)
                              .containsEntry(PARTITION_ID_2, PartitionHealthStatus.HEALTHY));
            });
  }

  @Test
  void shouldDeactivateAllLocalPartitionsAndFailStartWhenAllPartitionsFail() {
    // given: both partitions fail to schedule, so none recover
    partitionManager =
        buildManager(
            new BrokerCfg(),
            new FailingActorSchedulingService(actorScheduler, PARTITION_ID, PARTITION_ID_2));

    // when
    final var startFuture = partitionManager.start();

    // then: start() fails since no partition recovered, but both partitions are still marked
    // INACTIVE so nothing assumes they are serving traffic
    assertThat(startFuture).failsWithin(Duration.ofSeconds(10));
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

    // and: both partitions are reported as DEAD, since neither recovered and nothing is left
    // running to ever bring them back - this is the signal that the mode-change bookkeeping
    // (which only checks the INACTIVE role above) otherwise misses
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionHealthStatuses())
                              .containsEntry(PARTITION_ID, PartitionHealthStatus.DEAD)
                              .containsEntry(PARTITION_ID_2, PartitionHealthStatus.DEAD));
            });
  }

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

  @Nested
  class Start {

    private AtomicReference<ActorFuture<Void>> startFuture;

    @BeforeEach
    void setUp() {
      startFuture = new AtomicReference<>();
    }

    @Test
    void shouldCompleteStartFutureOnSuccess() {
      // when
      controlActor.run(() -> startFuture.set(partitionManager.start()));

      // then
      awaitStart();
      assertThat(startFuture.get().isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldCompleteImmediatelyWhenNoLocalPartitions() {
      // given
      when(clusterConfigurationService.getPartitionDistribution())
          .thenReturn(new PartitionDistribution(Set.of()));

      // when
      controlActor.run(() -> startFuture.set(partitionManager.start()));

      // then
      await()
          .atMost(Duration.ofSeconds(1))
          .until(() -> startFuture.get() != null && startFuture.get().isDone());
      assertThat(startFuture.get().isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldFailStartWhenDeactivationFails() {
      // given
      topologyManager.closeAsync().join();

      // when
      controlActor.run(() -> startFuture.set(partitionManager.start()));

      // then
      awaitStart();
      assertThat(startFuture.get().isCompletedExceptionally()).isTrue();
    }

    @Test
    void shouldStopCleanlyAfterDeactivationFailure() {
      // given
      topologyManager.closeAsync().join();

      // when
      controlActor.run(() -> startFuture.set(partitionManager.start()));
      awaitStart();

      // then
      assertThat(startFuture.get().isCompletedExceptionally()).isTrue();
      assertThat(partitionManager.stop()).succeedsWithin(Duration.ofSeconds(5));
    }

    private void awaitStart() {
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> startFuture.get() != null && startFuture.get().isDone());
    }
  }
}
