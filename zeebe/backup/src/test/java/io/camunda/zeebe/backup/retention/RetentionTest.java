/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.retention;

import static io.camunda.zeebe.backup.retention.RetentionMetrics.BACKUPS_DELETED_ROUND;
import static io.camunda.zeebe.backup.retention.RetentionMetrics.EARLIEST_BACKUP_ID;
import static io.camunda.zeebe.backup.retention.RetentionMetrics.PARTITION_TAG;
import static io.camunda.zeebe.backup.retention.RetentionMetrics.RETENTION_LAST_EXECUTION;
import static io.camunda.zeebe.backup.retention.RetentionMetrics.RETENTION_NEXT_EXECUTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.client.api.BackupDeleteRequest;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.schedule.Schedule.IntervalSchedule;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
public class RetentionTest {

  private static final int[] DEFAULT_BACKUP_OFFSETS = {375, 315, 255, 195, 50, 30, 10};

  @RegisterExtension
  public final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension();

  @Mock private BackupStore backupStore;
  @Mock private BrokerClient brokerClient;
  @Mock private BrokerTopologyManager topologyManager;
  @Mock private BrokerClusterState clusterState;
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private BackupRetention backupRetention;

  @BeforeEach
  void setUp() {
    meterRegistry.clear();
    backupRetention = createBackupRetention();

    doReturn(clusterState).when(topologyManager).getTopology();
    doReturn(List.of(1)).when(clusterState).getPartitions();

    lenient()
        .doReturn(CompletableFuture.completedFuture(null))
        .when(brokerClient)
        .sendRequestWithRetry(any());

    actorScheduler.getClock().pinCurrentTime();
  }

  @Test
  void shouldPerformAllActionsSinglePartition() {
    // given
    final var now = actorScheduler.getClock().instant();
    final List<BackupStatus> backups = createDefaultBackups(1, 1, now);

    when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(backups));

    // when
    runRetentionCycle();

    // then — first 4 backups (offsets 375, 315, 255, 195) are outside the 1-minute window
    // They have 4 distinct checkpoint IDs, so 4 DELETE_BACKUP commands should be sent
    verifyDeleteCommandsSent(1, backups.subList(0, 4));
  }

  @Test
  void shouldPerformAllActionsSinglePartitionWithoutCreatedDate() {
    // given
    final var now = actorScheduler.getClock().instant();
    final List<BackupStatus> backups = createDefaultBackupsNoCreatedDate(1, 1, now);

    when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(backups));

    // when
    runRetentionCycle();

    // then
    verifyDeleteCommandsSent(1, backups.subList(0, 4));
  }

  @Test
  void shouldPerformAllActionsMultiPartition() {
    // given
    setupMultiPartition(List.of(1, 2, 3));
    final var now = actorScheduler.getClock().instant();
    final Map<Integer, List<BackupStatus>> backupsPerPartition =
        createBackupsForPartitions(List.of(1, 2, 3), now);

    setupBackupStoreListForPartitions(backupsPerPartition);

    // when
    runRetentionCycle();

    // then
    backupsPerPartition.forEach(
        (partition, backups) -> verifyDeleteCommandsSent(partition, backups.subList(0, 4)));
  }

  @Test
  void shouldHandleNoBackups() {
    // given
    when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

    // when
    runRetentionCycle();

    // then
    verify(backupStore).list(any());
    verifyNoDeleteCommands();
  }

  @Test
  void shouldRegisterMetricsOnCreation() {
    // given
    final var partitions = List.of(1, 2, 3);
    setupMultiPartition(partitions);
    final var now = actorScheduler.getClock().instant();
    final Map<Integer, List<BackupStatus>> backupsPerPartition =
        createBackupsForPartitions(partitions, now);

    setupBackupStoreListForPartitions(backupsPerPartition);

    // when
    runRetentionCycle();

    // then
    assertThat(getGauge(RETENTION_LAST_EXECUTION)).isNotNull();
    assertThat(getGauge(RETENTION_NEXT_EXECUTION)).isNotNull();
    partitions.forEach(
        partition -> {
          assertThat(getGauge(BACKUPS_DELETED_ROUND, partition)).isNotNull();
          assertThat(getGauge(EARLIEST_BACKUP_ID, partition)).isNotNull();
        });
  }

  @Test
  void shouldDeRegisterMetricsOnShutdown() {
    // given
    final var partitions = List.of(1, 2, 3);
    setupMultiPartition(partitions);
    final var now = actorScheduler.getClock().instant();
    final Map<Integer, List<BackupStatus>> backupsPerPartition =
        createBackupsForPartitions(partitions, now);

    setupBackupStoreListForPartitions(backupsPerPartition);

    // when
    runRetentionCycle();
    backupRetention.closeAsync();
    actorScheduler.workUntilDone();

    // then
    assertThatThrownBy(() -> getGauge(RETENTION_LAST_EXECUTION))
        .isInstanceOf(MeterNotFoundException.class);
    assertThatThrownBy(() -> getGauge(RETENTION_NEXT_EXECUTION))
        .isInstanceOf(MeterNotFoundException.class);

    partitions.forEach(
        partition -> {
          assertThatThrownBy(() -> getGauge(BACKUPS_DELETED_ROUND, partition))
              .isInstanceOf(MeterNotFoundException.class);
          assertThatThrownBy(() -> getGauge(EARLIEST_BACKUP_ID, partition))
              .isInstanceOf(MeterNotFoundException.class);
        });
  }

  @Test
  void shouldProgressMarkerAfterFailed() {
    // given
    final var now = actorScheduler.getClock().instant();
    final var backup1 = backup(now.minusSeconds(300));
    final var backup2 = failedBackup(now.minusSeconds(60));
    final var backup3 = failedBackup(now.minusSeconds(50));
    final var backup4 = backup(now.minusSeconds(40));

    when(backupStore.list(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup1, backup2, backup3, backup4)));

    // when
    runRetentionCycle();
    actorScheduler.workUntilDone();

    // then — only backup1 is outside the retention window (300s > 60s window relative to backup4)
    verifyDeleteCommandSent(1, backup1.id().checkpointId());
    verifyNoDeleteCommandSent(backup4.id().checkpointId());
  }

  @Test
  void shouldIgnoreLastFailedBackup() {
    // given
    final var now = actorScheduler.getClock().instant();
    final var backup1 = backup(now.minusSeconds(300));
    final var backup2 = backup(now.minusSeconds(100));
    final var backup3 = failedBackup(now.minusSeconds(40));

    when(backupStore.list(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup1, backup2, backup3)));

    // when
    runRetentionCycle();
    actorScheduler.workUntilDone();

    // then — backup1 is outside the window (300s - 100s = 200s > 60s window); backup2 is the
    // latest completed so it's kept
    verifyDeleteCommandSent(1, backup1.id().checkpointId());
    verifyNoDeleteCommandSent(backup2.id().checkpointId());
  }

  private Gauge getGauge(final String gaugeName) {
    return meterRegistry.get(gaugeName).gauge();
  }

  private Gauge getGauge(final String gaugeName, final int partitionId) {
    return meterRegistry.get(gaugeName).tag(PARTITION_TAG, String.valueOf(partitionId)).gauge();
  }

  private BackupRetention createBackupRetention() {
    return new BackupRetention(
        backupStore,
        brokerClient,
        new IntervalSchedule(Duration.ofSeconds(10)),
        Duration.ofMinutes(1),
        topologyManager,
        meterRegistry);
  }

  private void setupMultiPartition(final List<Integer> partitions) {
    reset(clusterState);
    doReturn(partitions).when(clusterState).getPartitions();
    backupRetention = createBackupRetention();
  }

  private void runRetentionCycle() {
    actorScheduler.submitActor(backupRetention);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(10));
    actorScheduler.workUntilDone();
  }

  private List<BackupStatus> createDefaultBackups(
      final int partition, final int nodeId, final Instant now) {
    return java.util.Arrays.stream(DEFAULT_BACKUP_OFFSETS)
        .mapToObj(offset -> backup(partition, nodeId, now.minusSeconds(offset)))
        .toList();
  }

  private List<BackupStatus> createDefaultBackupsNoCreatedDate(
      final int partition, final int nodeId, final Instant now) {
    return java.util.Arrays.stream(DEFAULT_BACKUP_OFFSETS)
        .mapToObj(offset -> backupNoDescriptor(partition, nodeId, now.minusSeconds(offset)))
        .toList();
  }

  private Map<Integer, List<BackupStatus>> createBackupsForPartitions(
      final List<Integer> partitions, final Instant now) {
    final Map<Integer, List<BackupStatus>> backupsPerPartition = new HashMap<>();
    for (final int partition : partitions) {
      backupsPerPartition.put(partition, createDefaultBackups(partition, 1, now));
    }
    return backupsPerPartition;
  }

  private void setupBackupStoreListForPartitions(
      final Map<Integer, List<BackupStatus>> backupsPerPartition) {
    backupsPerPartition.forEach(
        (partition, backups) ->
            doReturn(CompletableFuture.completedFuture(backups))
                .when(backupStore)
                .list(argThat(id -> id.partitionId().get() == partition)));
  }

  /** Verifies that DELETE_BACKUP commands were sent for each unique checkpoint ID in the list. */
  private void verifyDeleteCommandsSent(
      final int partitionId, final List<BackupStatus> deletedBackups) {
    final var uniqueCheckpointIds =
        deletedBackups.stream().mapToLong(b -> b.id().checkpointId()).distinct().toArray();
    for (final var checkpointId : uniqueCheckpointIds) {
      verifyDeleteCommandSent(partitionId, checkpointId);
    }
  }

  @SuppressWarnings("unchecked")
  private void verifyDeleteCommandSent(final int partitionId, final long checkpointId) {
    verify(brokerClient)
        .sendRequestWithRetry(
            (BrokerRequest<BackupStatusResponse>)
                argThat(
                    req ->
                        req instanceof BackupDeleteRequest deleteReq
                            && deleteReq.getPartitionId() == partitionId
                            && deleteReq.getBackupId() == checkpointId));
  }

  @SuppressWarnings("unchecked")
  private void verifyNoDeleteCommandSent(final long checkpointId) {
    verify(brokerClient, never())
        .sendRequestWithRetry(
            (BrokerRequest<BackupStatusResponse>)
                argThat(
                    req ->
                        req instanceof BackupDeleteRequest deleteReq
                            && deleteReq.getBackupId() == checkpointId));
  }

  @SuppressWarnings("unchecked")
  private void verifyNoDeleteCommandSent(final int partitionId, final long checkpointId) {
    verify(brokerClient, never())
        .sendRequestWithRetry(
            (BrokerRequest<BackupStatusResponse>)
                argThat(
                    req ->
                        req instanceof BackupDeleteRequest deleteReq
                            && deleteReq.getPartitionId() == partitionId
                            && deleteReq.getBackupId() == checkpointId));
  }

  private void verifyNoDeleteCommands() {
    verify(brokerClient, never()).sendRequestWithRetry(any());
  }

  private BackupStatus backup(final Instant timestamp) {
    return backup(1, 1, timestamp);
  }

  private BackupStatus failedBackup(final Instant timestamp) {
    final var descriptor =
        new BackupDescriptorImpl(
            10L, 3, VersionUtil.getVersion(), timestamp, CheckpointType.SCHEDULED_BACKUP);
    return backup(1, 1, Optional.of(descriptor), timestamp, BackupStatusCode.FAILED);
  }

  private BackupStatus backup(final int partition, final int nodeId, final Instant timestamp) {
    final var descriptor =
        new BackupDescriptorImpl(
            10L, 3, VersionUtil.getVersion(), timestamp, CheckpointType.SCHEDULED_BACKUP);
    return backup(
        partition, nodeId, Optional.of(descriptor), timestamp, BackupStatusCode.COMPLETED);
  }

  private BackupStatus backupNoDescriptor(
      final int partition, final int nodeId, final Instant timestamp) {
    return backup(partition, nodeId, Optional.empty(), timestamp, BackupStatusCode.COMPLETED);
  }

  private BackupStatus backupOnlyId(
      final int partition, final int nodeId, final Instant timestamp) {
    return backup(partition, nodeId, Optional.empty(), timestamp, BackupStatusCode.COMPLETED);
  }

  private BackupStatus backup(
      final int partition,
      final int nodeId,
      final Optional<BackupDescriptor> descriptor,
      final Instant timestamp,
      final BackupStatusCode backupStatusCode) {
    return new BackupStatusImpl(
        new BackupIdentifierImpl(nodeId, partition, timestamp.toEpochMilli()),
        descriptor,
        backupStatusCode,
        null,
        Optional.empty(),
        Optional.of(timestamp));
  }

  @Nested
  class ErrorHandling {
    @Test
    void actorShouldNotHangOnBackupListingFailure() {
      // given
      reset(backupStore);
      when(backupStore.list(any()))
          .thenReturn(
              CompletableFuture.failedFuture(new RuntimeException("Failed to list backups")));

      // when
      runRetentionCycle();

      // then
      actorScheduler.updateClock(Duration.ofSeconds(10));
      actorScheduler.workUntilDone();
      verify(backupStore, times(2)).list(any());
      verifyNoDeleteCommands();
    }

    @Test
    void shouldPerformAllActionOnOnePartition() {
      // given
      setupMultiPartition(List.of(1, 2));
      final var now = actorScheduler.getClock().instant();
      final Map<Integer, List<BackupStatus>> backupsPerPartition =
          createBackupsForPartitions(List.of(1, 2), now);

      doReturn(CompletableFuture.completedFuture(backupsPerPartition.get(1)))
          .when(backupStore)
          .list(argThat(id -> id.partitionId().get() == 1));

      doReturn(
              CompletableFuture.failedFuture(
                  new RuntimeException("Failed to list backups for partition 2")))
          .when(backupStore)
          .list(argThat(id -> id.partitionId().get() == 2));

      // when
      runRetentionCycle();

      // then — delete commands sent for partition 1 but not partition 2
      verifyDeleteCommandsSent(1, backupsPerPartition.get(1).subList(0, 4));

      // No delete commands for partition 2's backups
      backupsPerPartition
          .get(2)
          .subList(0, 4)
          .forEach(backup -> verifyNoDeleteCommandSent(2, backup.id().checkpointId()));
    }
  }

  @Nested
  @MockitoSettings(strictness = Strictness.LENIENT)
  class AtLeastOneAvailable {

    @Test
    void shouldAlwaysMaintainASingleBackup() {
      // given
      final var now = actorScheduler.getClock().instant();
      final var backup1 = backup(now.minusSeconds(200));
      final var backup2 = backup(now.minusSeconds(140));
      final var backup3 = backup(now.minusSeconds(70));
      // Failed backup with older timestamp than the latest successful backup
      final var backup4 = failedBackup(now.minusSeconds(10));

      when(backupStore.list(any()))
          .thenReturn(
              CompletableFuture.completedFuture(List.of(backup1, backup2, backup3, backup4)));

      // when
      runRetentionCycle();

      // then
      verifyDeleteCommandSent(1, backup1.id().checkpointId());
      verifyDeleteCommandSent(1, backup2.id().checkpointId());
      verifyNoDeleteCommandSent(backup4.id().checkpointId());
      verifyNoDeleteCommandSent(backup3.id().checkpointId());
    }

    @Test
    void shouldAlwaysMaintainASingleBackupWhenNoDescriptor() {
      // given
      final var now = actorScheduler.getClock().instant();
      final var backup1 = backupNoDescriptor(1, 1, now.minusSeconds(300));
      final var backup2 = backupNoDescriptor(1, 1, now.minusSeconds(200));
      final var backup3 = backupNoDescriptor(1, 1, now.minusSeconds(130));

      when(backupStore.list(any()))
          .thenReturn(CompletableFuture.completedFuture(List.of(backup1, backup2, backup3)));

      // when
      runRetentionCycle();

      // then
      verifyDeleteCommandSent(1, backup1.id().checkpointId());
      verifyDeleteCommandSent(1, backup2.id().checkpointId());
      verifyNoDeleteCommandSent(backup3.id().checkpointId());
    }

    @Test
    void shouldNotDeleteBackupsIfOnlyOneExists() {
      // given
      final var now = actorScheduler.getClock().instant();
      final var backup1 = backup(now.minusSeconds(500));

      when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(List.of(backup1)));

      // when multiple runs occur
      actorScheduler.submitActor(backupRetention);
      actorScheduler.workUntilDone();
      actorScheduler.updateClock(Duration.ofSeconds(10));
      actorScheduler.workUntilDone();
      actorScheduler.updateClock(Duration.ofSeconds(10));
      actorScheduler.workUntilDone();
      actorScheduler.updateClock(Duration.ofSeconds(10));
      actorScheduler.workUntilDone();

      // then
      verifyNoDeleteCommands();
    }

    @Test
    void shouldAlwaysMaintainASingleBackupOnCheckpointId() {
      // given
      final var now = actorScheduler.getClock().instant();
      final var backup1 = backup(now.minusSeconds(300));
      final var backup2 = backup(now.minusSeconds(200));
      final var backup3 = backupOnlyId(1, 1, now.minusSeconds(130));

      when(backupStore.list(any()))
          .thenReturn(CompletableFuture.completedFuture(List.of(backup1, backup2, backup3)));

      // when
      runRetentionCycle();

      // then
      verifyDeleteCommandSent(1, backup1.id().checkpointId());
      verifyDeleteCommandSent(1, backup2.id().checkpointId());
      verifyNoDeleteCommandSent(backup3.id().checkpointId());
    }

    @Test
    void shouldNotInterfereIfLatestNotWithinWindow() {
      // given
      final var now = actorScheduler.getClock().instant();
      final var backup1 = backup(now.minusSeconds(290));
      final var backup2 = backup(now.minusSeconds(220));
      final var backup3 = backup(now.minusSeconds(150));
      final var backup4 = backup(now.minusSeconds(100));

      when(backupStore.list(any()))
          .thenReturn(
              CompletableFuture.completedFuture(List.of(backup1, backup2, backup3, backup4)));

      // when
      runRetentionCycle();

      // then
      verifyDeleteCommandSent(1, backup1.id().checkpointId());
      verifyDeleteCommandSent(1, backup2.id().checkpointId());
      verifyNoDeleteCommandSent(backup3.id().checkpointId());
      verifyNoDeleteCommandSent(backup4.id().checkpointId());
    }

    @Test
    void shouldNotDeleteBackupsRelativeToLastCompleted() {
      // given
      final var now = actorScheduler.getClock().instant();
      final var backup1 = backup(now.minusSeconds(370));
      final var backup2 = backup(now.minusSeconds(340));
      final var backup3 = backup(now.minusSeconds(300));
      final var backup4 = failedBackup(now.minusSeconds(220));
      final var backup5 = failedBackup(now.minusSeconds(210));

      when(backupStore.list(any()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  List.of(backup1, backup2, backup3, backup4, backup5)));

      // when
      runRetentionCycle();
      actorScheduler.workUntilDone();

      // then — window is relative to backup3 (latest completed, 300s ago)
      // Window bound = 300 + 60 = 360s ago; backup1 (370s) is outside
      verifyDeleteCommandSent(1, backup1.id().checkpointId());
      verifyNoDeleteCommandSent(backup2.id().checkpointId());
      verifyNoDeleteCommandSent(backup3.id().checkpointId());
      verifyNoDeleteCommandSent(backup4.id().checkpointId());
      verifyNoDeleteCommandSent(backup5.id().checkpointId());
    }
  }
}
