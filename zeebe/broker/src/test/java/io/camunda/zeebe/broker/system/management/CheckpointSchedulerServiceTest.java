/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
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
  private static final String OTHER_TENANT_ID = "tenant-a";

  private CheckpointSchedulingService schedulingService;
  private final Map<String, BackupCfg> backupCfgByTenant = new LinkedHashMap<>();
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
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

    backupCfgByTenant.put(DEFAULT_PHYSICAL_TENANT_ID, backupCfg("base-path"));

    doReturn(MemberId.from("0")).when(member1).id();
    doReturn(MemberId.from("1")).when(member2).id();
    doReturn(MemberId.from("2")).when(member3).id();
    doNothing().when(membershipService).addListener(any());
    doReturn(Set.of(partition)).when(mockPartitionManager).getZeebePartitions();

    doReturn(CONCURRENCY_CONTROL.completedFuture(null)).when(scheduler).submitActor(any());

    schedulingService = createSchedulingService();
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
  void shouldStartSchedulersForEveryPhysicalTenant() {
    // given
    backupCfgByTenant.put(OTHER_TENANT_ID, backupCfg("other-base-path"));
    schedulingService = createSchedulingService();
    doReturn(Set.of(member1)).when(membershipService).getMembers();
    doReturn(member1).when(membershipService).getLocalMember();

    // when
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              verify(scheduler, times(2))
                  .submitActor(
                      argThat(CheckpointScheduler.class::isInstance), eq(SchedulingHints.IO_BOUND));
              verify(scheduler, times(2))
                  .submitActor(
                      argThat(BackupRetention.class::isInstance), eq(SchedulingHints.IO_BOUND));
            });
  }

  @Test
  void shouldReportMetricsPerPhysicalTenant() {
    // given
    backupCfgByTenant.put(OTHER_TENANT_ID, backupCfg("other-base-path"));
    schedulingService = createSchedulingService();
    doReturn(Set.of(member1)).when(membershipService).getMembers();
    doReturn(member1).when(membershipService).getLocalMember();

    // when
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // then — the tenants report the same metrics, told apart by the physical tenant tag
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(
                        meterRegistry
                            .find("camunda.checkpoint.scheduler.last.checkpoint.id")
                            .gauges())
                    .extracting(gauge -> gauge.getId().getTag("physicalTenant"))
                    .contains(DEFAULT_PHYSICAL_TENANT_ID, OTHER_TENANT_ID));
    assertThat(meterRegistry.find("camunda.backup.retention.next.execution.millis").gauges())
        .extracting(gauge -> gauge.getId().getTag("physicalTenant"))
        .containsExactlyInAnyOrder(DEFAULT_PHYSICAL_TENANT_ID, OTHER_TENANT_ID);
  }

  @Test
  void shouldOnlyStartSchedulersConfiguredForTheTenant() {
    // given — the other tenant only schedules checkpoints, it has no retention configured
    final var otherCfg = backupCfg("other-base-path");
    otherCfg.setRetention(new BackupSchedulerRetentionCfg());
    backupCfgByTenant.put(OTHER_TENANT_ID, otherCfg);
    schedulingService = createSchedulingService();
    doReturn(Set.of(member1)).when(membershipService).getMembers();
    doReturn(member1).when(membershipService).getLocalMember();

    // when
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              verify(scheduler, times(2))
                  .submitActor(
                      argThat(CheckpointScheduler.class::isInstance), eq(SchedulingHints.IO_BOUND));
              verify(scheduler, times(1))
                  .submitActor(
                      argThat(BackupRetention.class::isInstance), eq(SchedulingHints.IO_BOUND));
            });
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
  void shouldStopSchedulersOfAllTenantsOnLowestAdded() {
    // given
    backupCfgByTenant.put(OTHER_TENANT_ID, backupCfg("other-base-path"));
    schedulingService = createSchedulingService();
    doReturn(member2).when(membershipService).getLocalMember();
    doReturn(Set.of(member2, member3)).when(membershipService).getMembers();
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();
    verify(scheduler, times(2))
        .submitActor(
            argThat(CheckpointScheduler.class::isInstance),
            argThat(arg -> arg.equals(SchedulingHints.IO_BOUND)));

    // when
    doReturn(Set.of(member1, member2, member3)).when(membershipService).getMembers();
    schedulingService.event(new ClusterMembershipEvent(Type.MEMBER_ADDED, member1));

    // then
    assertThat(schedulingService.schedulerActors()).allMatch(Actor::isActorClosed);
  }

  @Test
  void shouldOnlyStartCheckpointSchedulerOnEmptyString()
      throws NoSuchFieldException, IllegalAccessException {
    // given
    backupCfgByTenant.get(DEFAULT_PHYSICAL_TENANT_ID).setSchedule("");

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
    backupCfgByTenant.get(DEFAULT_PHYSICAL_TENANT_ID).setSchedule("none");
    schedulingService = createSchedulingService();

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

    backupCfgByTenant.get(DEFAULT_PHYSICAL_TENANT_ID).setCheckpointInterval(null);

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
    backupCfgByTenant.get(DEFAULT_PHYSICAL_TENANT_ID).getRetention().setCleanupSchedule(null);
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
    backupCfgByTenant.get(DEFAULT_PHYSICAL_TENANT_ID).getRetention().setWindow(null);
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
  void shouldStartRetentionJobWithoutCheckpointOrBackupSchedule() {
    // given — an ES/OS style setup: retention only, no checkpoint interval and no backup schedule
    backupCfgByTenant.put(DEFAULT_PHYSICAL_TENANT_ID, retentionOnlyBackupCfg("retention-only"));
    schedulingService = createSchedulingService();
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
                        argThat(BackupRetention.class::isInstance), eq(SchedulingHints.IO_BOUND)));
    verify(scheduler, never()).submitActor(argThat(CheckpointScheduler.class::isInstance), any());
  }

  @Test
  void shouldRelocateStandaloneRetentionJobOnMembershipChange() {
    // given — retention only, and this broker is not the lowest member
    backupCfgByTenant.put(DEFAULT_PHYSICAL_TENANT_ID, retentionOnlyBackupCfg("retention-only"));
    schedulingService = createSchedulingService();
    doReturn(member2).when(membershipService).getLocalMember();
    doReturn(Set.of(member2, member3)).when(membershipService).getMembers();
    schedulingService.onActorStarting();
    schedulingService.onActorStarted();
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(scheduler, times(1))
                    .submitActor(
                        argThat(BackupRetention.class::isInstance), eq(SchedulingHints.IO_BOUND)));

    // when — a lower member joins
    doReturn(Set.of(member1, member2, member3)).when(membershipService).getMembers();
    schedulingService.event(new ClusterMembershipEvent(Type.MEMBER_ADDED, member1));

    // then
    assertThat(schedulingService.schedulerActors()).allMatch(Actor::isActorClosed);
  }

  private CheckpointSchedulingService createSchedulingService() {
    return new CheckpointSchedulingService(
        membershipService, scheduler, backupCfgByTenant, brokerClient, meterRegistry);
  }

  private BackupCfg backupCfg(final String basePath) {
    final var brokerConfig = new BrokerCfg();
    brokerConfig.getData().setBackup(new BackupCfg());
    final var backupCfg = brokerConfig.getData().getBackup();
    backupCfg.setSchedule("PT10M");
    backupCfg.setCheckpointInterval(Duration.ofMinutes(1L));
    backupCfg.setContinuous(true);
    backupCfg.setRetention(new BackupSchedulerRetentionCfg());
    backupCfg.getRetention().setCleanupSchedule("PT10M");
    backupCfg.getRetention().setWindow(Duration.ofHours(1L));
    backupCfg.setStore(BackupStoreType.FILESYSTEM);
    backupCfg.getFilesystem().setBasePath(basePath);
    return backupCfg;
  }

  /** Retention configured on its own, as an Elasticsearch/OpenSearch installation would have it. */
  private BackupCfg retentionOnlyBackupCfg(final String basePath) {
    final var backupCfg = backupCfg(basePath);
    backupCfg.setContinuous(false);
    backupCfg.setSchedule(null);
    backupCfg.setCheckpointInterval(null);
    return backupCfg;
  }

  private Schedule getSchedule(
      final CheckpointSchedulingService schedulingService, final String scheduleName)
      throws NoSuchFieldException, IllegalAccessException {
    final Field scheduleField = CheckpointScheduler.class.getDeclaredField(scheduleName);
    scheduleField.setAccessible(true);
    final var scheduler =
        schedulingService.schedulerActors().stream()
            .filter(CheckpointScheduler.class::isInstance)
            .findFirst()
            .orElseThrow();
    return (Schedule) scheduleField.get(scheduler);
  }
}
