/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.impl.DefaultClusterMembershipService;
import io.camunda.zeebe.backup.retention.BackupRetention;
import io.camunda.zeebe.backup.schedule.CheckpointScheduler;
import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.BackupSchedulerRetentionCfg;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
public class CheckpointSchedulerServiceTest {
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private CheckpointSchedulingService schedulingService;
  private final BrokerCfg brokerConfig = new BrokerCfg();
  private BrokerClient brokerClient;
  private DefaultClusterMembershipService membershipService;
  private ActorScheduler scheduler;
  private PartitionManager mockPartitionManager;
  private Member member1;
  private Member member2;
  private Member member3;

  @AfterEach
  public void tearDown() throws ExecutionException, InterruptedException {
    scheduler.stop().get();
  }

  @BeforeEach
  public void setup() {
    brokerClient = mock(BrokerClient.class);
    mockPartitionManager = mock(PartitionManager.class);
    final var partition = mock(ZeebePartition.class);
    membershipService = mock(DefaultClusterMembershipService.class);
    member1 = mock(Member.class);
    member2 = mock(Member.class);
    member3 = mock(Member.class);
    scheduler = spy(ActorScheduler.newActorScheduler().build());
    scheduler.start();

    brokerConfig.getData().setBackup(new BackupCfg());
    brokerConfig.getData().getBackup().setSchedule("PT10M");
    brokerConfig.getData().getBackup().setCheckpointInterval(Duration.ofMinutes(1L));
    brokerConfig.getData().getBackup().setContinuous(true);
    brokerConfig.getData().getBackup().setRetention(new BackupSchedulerRetentionCfg());
    brokerConfig.getData().getBackup().getRetention().setCleanupSchedule("PT10M");
    brokerConfig.getData().getBackup().getRetention().setWindow(Duration.ofHours(1L));
    brokerConfig.getData().getBackup().setStore(BackupStoreType.FILESYSTEM);
    brokerConfig.getData().getBackup().getFilesystem().setBasePath("base-path");

    doReturn(MemberId.from("0")).when(member1).id();
    doReturn(MemberId.from("1")).when(member2).id();
    doReturn(MemberId.from("2")).when(member3).id();
    doNothing().when(membershipService).addListener(any());
    doReturn(Set.of(partition)).when(mockPartitionManager).getZeebePartitions();

    doReturn(CONCURRENCY_CONTROL.completedFuture(null)).when(scheduler).submitActor(any());

    schedulingService =
        new CheckpointSchedulingService(
            membershipService,
            scheduler,
            brokerConfig.getData().getBackup(),
            brokerClient,
            new SimpleMeterRegistry());
  }

  @Test
  void shouldStartSchedulersInSingleBrokerCluster() {
    // given
    doReturn(Set.of(member1)).when(membershipService).getMembers();
    doReturn(member1).when(membershipService).getLocalMember();

    // when
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(scheduler, times(1))
                    .submitActor(
                        argThat(CheckpointScheduler.class::isInstance),
                        argThat(arg -> arg.equals(SchedulingHints.IO_BOUND))));

    verify(scheduler, times(1))
        .submitActor(
            argThat(BackupRetention.class::isInstance),
            argThat(arg -> arg.equals(SchedulingHints.IO_BOUND)));
  }

  @Test
  void shouldNotStartSchedulersIfNotTheLowestOnRemoval() {
    // given
    doReturn(member2).when(membershipService).getLocalMember();
    doReturn(Set.of(member1, member2, member3)).when(membershipService).getMembers();
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // when
    doReturn(member2).when(membershipService).getLocalMember();
    doReturn(Set.of(member1, member2)).when(membershipService).getMembers();
    schedulingService.event(
        new ClusterMembershipEvent(ClusterMembershipEvent.Type.MEMBER_REMOVED, member3));

    // then
    verify(scheduler, times(0)).submitActor(argThat(CheckpointScheduler.class::isInstance), any());
    verify(scheduler, times(0)).submitActor(argThat(BackupRetention.class::isInstance), any());
  }

  @Test
  void shouldStartSchedulersIfLowestOnRemoval() {
    // given
    doReturn(member2).when(membershipService).getLocalMember();
    doReturn(Set.of(member1, member2, member3)).when(membershipService).getMembers();
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // when
    doReturn(member2).when(membershipService).getLocalMember();
    doReturn(Set.of(member2, member3)).when(membershipService).getMembers();
    schedulingService.event(
        new ClusterMembershipEvent(ClusterMembershipEvent.Type.MEMBER_REMOVED, member1));

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(scheduler, times(1))
                    .submitActor(
                        argThat(CheckpointScheduler.class::isInstance),
                        argThat(arg -> arg.equals(SchedulingHints.IO_BOUND))));
    verify(scheduler, times(1))
        .submitActor(
            argThat(BackupRetention.class::isInstance),
            argThat(arg -> arg.equals(SchedulingHints.IO_BOUND)));
  }

  @Test
  void shouldStartSchedulersIfLowestWhenAdded() {
    // given

    doReturn(member1).when(membershipService).getLocalMember();
    doReturn(Set.of(member2, member3)).when(membershipService).getMembers();
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // when
    doReturn(member1).when(membershipService).getLocalMember();
    doReturn(Set.of(member1, member2, member3)).when(membershipService).getMembers();
    schedulingService.event(new ClusterMembershipEvent(Type.MEMBER_ADDED, member1));

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(scheduler, times(1))
                    .submitActor(
                        argThat(CheckpointScheduler.class::isInstance),
                        argThat(arg -> arg.equals(SchedulingHints.IO_BOUND))));
    verify(scheduler, times(1))
        .submitActor(
            argThat(BackupRetention.class::isInstance),
            argThat(arg -> arg.equals(SchedulingHints.IO_BOUND)));
  }

  @Test
  void shouldNotStartSchedulersIfNotLowestWhenAdded() {
    // given

    doReturn(member2).when(membershipService).getLocalMember();
    doReturn(Set.of(member1, member3)).when(membershipService).getMembers();
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // when
    doReturn(member2).when(membershipService).getLocalMember();
    doReturn(Set.of(member1, member2, member3)).when(membershipService).getMembers();
    schedulingService.event(new ClusterMembershipEvent(Type.MEMBER_ADDED, member2));

    // then
    verify(scheduler, times(0)).submitActor(argThat(CheckpointScheduler.class::isInstance), any());
    verify(scheduler, times(0)).submitActor(argThat(BackupRetention.class::isInstance), any());
  }

  @Test
  void shouldStopSchedulerOnLowestAdded() throws NoSuchFieldException, IllegalAccessException {
    // given

    doReturn(member2).when(membershipService).getLocalMember();
    doReturn(Set.of(member2, member3)).when(membershipService).getMembers();
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();
    verify(scheduler, times(1))
        .submitActor(
            argThat(CheckpointScheduler.class::isInstance),
            argThat(arg -> arg.equals(SchedulingHints.IO_BOUND)));
    final var checkpointCreatorSpy = getCheckpointCreator(schedulingService);
    final var retentionJobSpy = getRetentionJob(schedulingService);

    // when
    doReturn(Set.of(member1, member2, member3)).when(membershipService).getMembers();
    schedulingService.event(new ClusterMembershipEvent(Type.MEMBER_ADDED, member1));

    // then
    assertThat(checkpointCreatorSpy.isActorClosed()).isTrue();
    assertThat(retentionJobSpy.isActorClosed()).isTrue();
  }

  @Test
  void shouldOnlyStartCheckpointSchedulerOnEmptyString()
      throws NoSuchFieldException, IllegalAccessException {
    // given
    brokerConfig.getData().getBackup().setSchedule("");

    final var member = mock(Member.class);
    doReturn(Set.of(member)).when(membershipService).getMembers();
    doReturn(MemberId.from("0")).when(member).id();
    doReturn(member).when(membershipService).getLocalMember();

    // when
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(scheduler, times(1))
                    .submitActor(
                        argThat(CheckpointScheduler.class::isInstance),
                        argThat(arg -> arg.equals(SchedulingHints.IO_BOUND))));
    assertThat(getSchedule(schedulingService, "checkpointSchedule")).isNotNull();
    assertThat(getSchedule(schedulingService, "backupSchedule")).isNull();
  }

  @Test
  void shouldOnlyStartCheckpointSchedulerOnNone()
      throws NoSuchFieldException, IllegalAccessException {
    // given

    brokerConfig.getData().getBackup().setSchedule("none");

    schedulingService =
        new CheckpointSchedulingService(
            membershipService,
            scheduler,
            brokerConfig.getData().getBackup(),
            brokerClient,
            new SimpleMeterRegistry());

    final var member = mock(Member.class);
    doReturn(Set.of(member)).when(membershipService).getMembers();
    doReturn(MemberId.from("0")).when(member).id();
    doReturn(member).when(membershipService).getLocalMember();

    // when
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(scheduler, times(1))
                    .submitActor(
                        argThat(CheckpointScheduler.class::isInstance),
                        argThat(arg -> arg.equals(SchedulingHints.IO_BOUND))));

    assertThat(getSchedule(schedulingService, "checkpointSchedule")).isNotNull();
    assertThat(getSchedule(schedulingService, "backupSchedule")).isNull();
  }

  @Test
  void shouldOnlyStartBackupScheduler() throws NoSuchFieldException, IllegalAccessException {
    // given

    brokerConfig.getData().getBackup().setCheckpointInterval(null);

    final var member = mock(Member.class);
    doReturn(Set.of(member)).when(membershipService).getMembers();
    doReturn(MemberId.from("0")).when(member).id();
    doReturn(member).when(membershipService).getLocalMember();

    // when

    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(scheduler, times(1))
                    .submitActor(
                        argThat(CheckpointScheduler.class::isInstance),
                        argThat(arg -> arg.equals(SchedulingHints.IO_BOUND))));
    assertThat(getSchedule(schedulingService, "checkpointSchedule")).isNull();
    assertThat(getSchedule(schedulingService, "backupSchedule")).isNotNull();
  }

  @Test
  void shouldNotRegisterRetentionJobOnEmptySchedule() {

    // given
    brokerConfig.getData().getBackup().getRetention().setCleanupSchedule(null);
    final var member = mock(Member.class);
    doReturn(Set.of(member)).when(membershipService).getMembers();
    doReturn(MemberId.from("0")).when(member).id();
    doReturn(member).when(membershipService).getLocalMember();

    // when
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(scheduler, times(1))
                    .submitActor(
                        argThat(CheckpointScheduler.class::isInstance),
                        argThat(arg -> arg.equals(SchedulingHints.IO_BOUND))));

    verify(scheduler, never()).submitActor(argThat(BackupRetention.class::isInstance), any());
  }

  @Test
  void shouldNotRegisterRetentionJobOnNullSchedule() {

    // given
    brokerConfig.getData().getBackup().getRetention().setWindow(null);
    final var member = mock(Member.class);
    doReturn(Set.of(member)).when(membershipService).getMembers();
    doReturn(MemberId.from("0")).when(member).id();
    doReturn(member).when(membershipService).getLocalMember();

    // when
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(scheduler, times(1))
                    .submitActor(
                        argThat(CheckpointScheduler.class::isInstance),
                        argThat(arg -> arg.equals(SchedulingHints.IO_BOUND))));

    verify(scheduler, never()).submitActor(argThat(BackupRetention.class::isInstance), any());
  }

  private CheckpointScheduler getCheckpointCreator(
      final CheckpointSchedulingService schedulingService)
      throws NoSuchFieldException, IllegalAccessException {
    final Field checkpointCreatorField =
        CheckpointSchedulingService.class.getDeclaredField("checkpointScheduler");
    checkpointCreatorField.setAccessible(true);
    return (CheckpointScheduler) checkpointCreatorField.get(schedulingService);
  }

  private BackupRetention getRetentionJob(final CheckpointSchedulingService schedulingService)
      throws NoSuchFieldException, IllegalAccessException {
    final Field retentionJobField =
        CheckpointSchedulingService.class.getDeclaredField("backupRetentionJob");
    retentionJobField.setAccessible(true);
    return (BackupRetention) retentionJobField.get(schedulingService);
  }

  private Schedule getSchedule(
      final CheckpointSchedulingService schedulingService, final String scheduleName)
      throws NoSuchFieldException, IllegalAccessException {
    final Field checkpointCreatorField =
        CheckpointSchedulingService.class.getDeclaredField("checkpointScheduler");
    final Field scheduleField = CheckpointScheduler.class.getDeclaredField(scheduleName);
    checkpointCreatorField.setAccessible(true);
    scheduleField.setAccessible(true);
    final CheckpointScheduler creator =
        (CheckpointScheduler) checkpointCreatorField.get(schedulingService);

    return (Schedule) scheduleField.get(creator);
  }
}
