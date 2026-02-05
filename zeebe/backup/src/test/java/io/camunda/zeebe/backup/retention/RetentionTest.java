/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.retention;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupRangeMarker;
import io.camunda.zeebe.backup.api.BackupRangeMarker.End;
import io.camunda.zeebe.backup.api.BackupRangeMarker.Start;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.schedule.Schedule.IntervalSchedule;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.util.VersionUtil;
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

@ExtendWith(MockitoExtension.class)
public class RetentionTest {

  private static final int[] DEFAULT_BACKUP_OFFSETS = {360, 300, 290, 130, 110, 20, 10};

  @RegisterExtension
  public final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension();

  @Mock private BackupStore backupStore;
  @Mock private BrokerTopologyManager topologyManager;
  @Mock private BrokerClusterState clusterState;
  private BackupRetention backupRetention;

  @BeforeEach
  void setUp() {
    backupRetention = createBackupRetention();

    doReturn(clusterState).when(topologyManager).getTopology();
    doReturn(List.of(1)).when(clusterState).getPartitions();

    doReturn(CompletableFuture.completedFuture(null)).when(backupStore).delete(any());
    doReturn(CompletableFuture.completedFuture(null))
        .when(backupStore)
        .storeRangeMarker(anyInt(), any());
    lenient()
        .doReturn(CompletableFuture.completedFuture(null))
        .when(backupStore)
        .deleteRangeMarker(anyInt(), any());
    doReturn(CompletableFuture.completedFuture(null))
        .when(backupStore)
        .deleteRangeMarker(anyInt(), any());
    actorScheduler.getClock().pinCurrentTime();
  }

  @Test
  void shouldPerformAllActionsSinglePartition() {
    // given
    final var now = actorScheduler.getClock().instant();
    final List<BackupStatus> backups = createDefaultBackups(1, 1, now);
    final List<BackupRangeMarker> ranges = createDefaultRangeMarkers(now);

    when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(backups));
    when(backupStore.rangeMarkers(1)).thenReturn(CompletableFuture.completedFuture(ranges));

    // when
    runRetentionCycle();

    // then
    verifyBackupsDeleted(backups.subList(0, 4));
    verifyRangeMarkerStored(1, backups.get(4));
    verifyRangeMarkerDeleted(1, backups.get(3));
    verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.getFirst())));
    verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.get(1))));
  }

  @Test
  void shouldPerformAllActionsSinglePartitionWithoutCreatedDate() {
    // given
    final var now = actorScheduler.getClock().instant();
    final List<BackupStatus> backups = createDefaultBackupsNoCreatedDate(1, 1, now);
    final List<BackupRangeMarker> ranges = createDefaultRangeMarkers(now);

    when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(backups));
    when(backupStore.rangeMarkers(1)).thenReturn(CompletableFuture.completedFuture(ranges));

    // when
    runRetentionCycle();

    // then
    verifyBackupsDeleted(backups.subList(0, 4));
    verifyRangeMarkerStored(1, backups.get(4));
    verifyRangeMarkerDeleted(1, backups.get(3));
  }

  @Test
  void shouldPerformAllActionsMultiPartition() {
    // given
    setupMultiPartition(List.of(1, 2, 3));
    final var now = actorScheduler.getClock().instant();
    final Map<Integer, List<BackupStatus>> backupsPerPartition =
        createBackupsForPartitions(List.of(1, 2, 3), now);
    final List<BackupRangeMarker> ranges = createDefaultRangeMarkers(now);

    setupBackupStoreListForPartitions(backupsPerPartition);
    when(backupStore.rangeMarkers(anyInt())).thenReturn(CompletableFuture.completedFuture(ranges));

    // when
    runRetentionCycle();

    // then
    backupsPerPartition.forEach(
        (partition, backups) -> verifyBackupsDeleted(backups.subList(0, 4)));

    for (int partition = 1; partition <= 3; partition++) {
      verifyRangeMarkerStored(partition, backupsPerPartition.get(partition).get(4));
      verify(backupStore)
          .deleteRangeMarker(eq(partition), argThat(c -> c.equals(ranges.getFirst())));
      verify(backupStore).deleteRangeMarker(eq(partition), argThat(c -> c.equals(ranges.get(1))));
      verifyRangeMarkerDeleted(partition, backupsPerPartition.get(1).get(3));
    }
  }

  @Test
  void shouldHandleNoEndMarker() {
    // given
    final var now = actorScheduler.getClock().instant();
    final BackupStatus backup1 = backup(now.minusSeconds(200));
    final BackupStatus backup2 = backup(now.minusSeconds(150));
    final BackupStatus backup3 = backup(now.minusSeconds(110));

    when(backupStore.list(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup1, backup2, backup3)));

    when(backupStore.rangeMarkers(1))
        .thenReturn(
            CompletableFuture.completedFuture(
                List.of(
                    new Start(now.minusSeconds(360).toEpochMilli()),
                    new End(now.minusSeconds(290).toEpochMilli()),
                    new Start(now.minusSeconds(130).toEpochMilli()))));

    // when
    runRetentionCycle();

    // then
    verify(backupStore).delete(backup1.id());
    verify(backupStore).delete(backup2.id());
    verifyRangeMarkerStored(1, backup3);

    verify(backupStore, atLeast(1))
        .deleteRangeMarker(
            anyInt(),
            argThat(
                marker ->
                    marker instanceof Start
                        && marker.checkpointId() == now.minusSeconds(130).toEpochMilli()));
  }

  @Test
  void shouldNotDeleteMarkerIfSameAsLastBackup() {
    // given
    reset(backupStore);
    final var now = actorScheduler.getClock().instant();
    final BackupStatus backup1 = backup(now.minusSeconds(200));
    final BackupStatus backup2 = backup(now.minusSeconds(150));
    final BackupStatus backup3 = backup(now.minusSeconds(110));

    final List<BackupRangeMarker> ranges =
        List.of(
            new Start(now.minusSeconds(360).toEpochMilli()),
            new End(now.minusSeconds(290).toEpochMilli()),
            new Start(now.minusSeconds(110).toEpochMilli()),
            new End(now.minusSeconds(110).toEpochMilli()));

    when(backupStore.list(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup1, backup2, backup3)));

    when(backupStore.rangeMarkers(1)).thenReturn(CompletableFuture.completedFuture(ranges));
    doReturn(CompletableFuture.completedFuture(null))
        .when(backupStore)
        .deleteRangeMarker(anyInt(), any());

    // when
    runRetentionCycle();

    // then
    verify(backupStore).delete(backup1.id());
    verify(backupStore).delete(backup2.id());
    verify(backupStore, never()).storeRangeMarker(eq(1), any());
    verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.getFirst())));
    verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.get(1))));
  }

  @Test
  void shouldHandleNoBackups() {
    // given
    reset(backupStore);
    when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

    // when
    runRetentionCycle();

    // then
    verify(backupStore).list(any());
    verifyNoBackupStoreModifications();
  }

  private BackupRetention createBackupRetention() {
    return new BackupRetention(
        backupStore,
        new IntervalSchedule(Duration.ofSeconds(10)),
        Duration.ofMinutes(2),
        0,
        topologyManager,
        new SimpleMeterRegistry());
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
        .mapToObj(offset -> backupNoCreatedDate(partition, nodeId, now.minusSeconds(offset)))
        .toList();
  }

  private List<BackupRangeMarker> createDefaultRangeMarkers(final Instant now) {
    return List.of(
        new Start(now.minusSeconds(360).toEpochMilli()),
        new End(now.minusSeconds(290).toEpochMilli()),
        new Start(now.minusSeconds(130).toEpochMilli()),
        new End(now.minusSeconds(20).toEpochMilli()),
        new Start(now.minusSeconds(10).toEpochMilli()),
        new End(now.minusSeconds(10).toEpochMilli()));
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

  private void verifyBackupsDeleted(final List<BackupStatus> backups) {
    backups.forEach(backup -> verify(backupStore).delete(argThat(id -> id.equals(backup.id()))));
  }

  private void verifyRangeMarkerStored(final int partition, final BackupStatus backup) {
    verify(backupStore)
        .storeRangeMarker(
            eq(partition),
            argThat(
                marker ->
                    marker.checkpointId() == backup.id().checkpointId()
                        && marker instanceof Start));
  }

  private void verifyRangeMarkerDeleted(final int partition, final BackupStatus backup) {
    verify(backupStore, atLeast(1))
        .deleteRangeMarker(
            eq(partition),
            argThat(
                marker ->
                    marker.checkpointId() == backup.id().checkpointId()
                        && marker instanceof Start));
  }

  private void verifyNoBackupStoreModifications() {
    verify(backupStore, never()).deleteRangeMarker(anyInt(), any());
    verify(backupStore, never()).delete(any());
    verify(backupStore, never()).storeRangeMarker(anyInt(), any());
  }

  private BackupStatus backup(final Instant timestamp) {
    return backup(1, 1, timestamp);
  }

  private BackupStatus backup(final int partition, final int nodeId, final Instant timestamp) {
    return new BackupStatusImpl(
        new BackupIdentifierImpl(nodeId, partition, timestamp.toEpochMilli()),
        Optional.of(
            new BackupDescriptorImpl(
                10L, 3, VersionUtil.getVersion(), timestamp, CheckpointType.SCHEDULED_BACKUP)),
        null,
        null,
        Optional.of(timestamp),
        null);
  }

  private BackupStatus backupNoCreatedDate(
      final int partition, final int nodeId, final Instant timestamp) {
    return new BackupStatusImpl(
        new BackupIdentifierImpl(nodeId, partition, timestamp.toEpochMilli()),
        Optional.empty(),
        null,
        null,
        Optional.empty(),
        null);
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
      verify(backupStore, org.mockito.Mockito.times(2)).list(any());
      verifyNoBackupStoreModifications();
    }

    @Test
    void actorShouldNotHangOnMarkerListingFailure() {
      // given
      reset(backupStore);
      when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
      when(backupStore.rangeMarkers(anyInt()))
          .thenReturn(
              CompletableFuture.failedFuture(new RuntimeException("Failed to list markers")));

      // when
      runRetentionCycle();

      // then
      actorScheduler.updateClock(Duration.ofSeconds(10));
      actorScheduler.workUntilDone();
      verify(backupStore, org.mockito.Mockito.times(2)).list(any());
      verifyNoBackupStoreModifications();
    }

    @Test
    void shouldPerformAllActionOnOnePartition() {
      // given
      setupMultiPartition(List.of(1, 2));
      final var now = actorScheduler.getClock().instant();
      final Map<Integer, List<BackupStatus>> backupsPerPartition =
          createBackupsForPartitions(List.of(1, 2), now);
      final List<BackupRangeMarker> ranges = createDefaultRangeMarkers(now);

      doReturn(CompletableFuture.completedFuture(backupsPerPartition.get(1)))
          .when(backupStore)
          .list(argThat(id -> id.partitionId().get() == 1));

      doReturn(
              CompletableFuture.failedFuture(
                  new RuntimeException("Failed to list backups for partition 2")))
          .when(backupStore)
          .list(argThat(id -> id.partitionId().get() == 2));

      when(backupStore.rangeMarkers(anyInt()))
          .thenReturn(CompletableFuture.completedFuture(ranges));

      // when
      runRetentionCycle();

      // then
      verifyBackupsDeleted(backupsPerPartition.get(1).subList(0, 4));

      backupsPerPartition
          .get(2)
          .subList(0, 4)
          .forEach(
              backup -> verify(backupStore, never()).delete(argThat(id -> id.equals(backup.id()))));

      verifyRangeMarkerStored(1, backupsPerPartition.get(1).get(4));
      verify(backupStore, never()).storeRangeMarker(eq(2), any());

      verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.getFirst())));
      verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.get(1))));
      verify(backupStore, never()).deleteRangeMarker(eq(2), any());

      verifyRangeMarkerDeleted(1, backupsPerPartition.get(1).get(3));
    }
  }
}
