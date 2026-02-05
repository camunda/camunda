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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RetentionTest {

  @RegisterExtension
  public final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension();

  @Mock private BackupStore backupStore;
  @Mock private BrokerTopologyManager topologyManager;
  @Mock private BrokerClusterState clusterState;
  private BackupRetention backupRetention;

  @BeforeEach
  void setUp() {
    backupRetention =
        new BackupRetention(
            backupStore,
            new IntervalSchedule(Duration.ofSeconds(10)),
            Duration.ofMinutes(2),
            0,
            topologyManager,
            new SimpleMeterRegistry());

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
    final BackupStatus backup1 = backup(now.minusSeconds(360));
    final BackupStatus backup2 = backup(now.minusSeconds(300));
    final BackupStatus backup3 = backup(now.minusSeconds(290));
    final BackupStatus backup4 = backup(now.minusSeconds(130));
    final BackupStatus backup5 = backup(now.minusSeconds(110));
    final BackupStatus backup6 = backup(now.minusSeconds(20));
    final BackupStatus backup7 = backup(now.minusSeconds(10));

    final List<BackupRangeMarker> ranges =
        List.of(
            new Start(now.minusSeconds(360).toEpochMilli()),
            new End(now.minusSeconds(290).toEpochMilli()),
            new Start(now.minusSeconds(130).toEpochMilli()),
            new End(now.minusSeconds(20).toEpochMilli()),
            new Start(now.minusSeconds(10).toEpochMilli()),
            new End(now.minusSeconds(10).toEpochMilli()));

    when(backupStore.list(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                List.of(backup1, backup2, backup3, backup4, backup5, backup6, backup7)));

    when(backupStore.rangeMarkers(1)).thenReturn(CompletableFuture.completedFuture(ranges));

    // when
    actorScheduler.submitActor(backupRetention);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(10));
    actorScheduler.workUntilDone();

    // then
    verify(backupStore).delete(backup1.id());
    verify(backupStore).delete(backup2.id());
    verify(backupStore).delete(backup3.id());
    verify(backupStore).delete(backup4.id());
    verify(backupStore)
        .storeRangeMarker(
            eq(1),
            argThat(
                marker ->
                    marker.checkpointId() == backup5.id().checkpointId()
                        && marker instanceof Start));

    verify(backupStore, atLeast(1))
        .deleteRangeMarker(
            eq(1),
            argThat(
                marker ->
                    marker.checkpointId() == backup4.id().checkpointId()
                        && marker instanceof Start));

    verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.getFirst())));
    verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.get(1))));
  }

  @Test
  void shouldPerformAllActionsSinglePartitionWithoutCreatedDate() {
    // given
    final var now = actorScheduler.getClock().instant();
    final BackupStatus backup1 = backupNoCreatedDate(1, 1, now.minusSeconds(360));
    final BackupStatus backup2 = backupNoCreatedDate(1, 1, now.minusSeconds(300));
    final BackupStatus backup3 = backupNoCreatedDate(1, 1, now.minusSeconds(290));
    final BackupStatus backup4 = backupNoCreatedDate(1, 1, now.minusSeconds(130));
    final BackupStatus backup5 = backupNoCreatedDate(1, 1, now.minusSeconds(110));
    final BackupStatus backup6 = backupNoCreatedDate(1, 1, now.minusSeconds(20));
    final BackupStatus backup7 = backupNoCreatedDate(1, 1, now.minusSeconds(10));

    final List<BackupRangeMarker> ranges =
        List.of(
            new Start(now.minusSeconds(360).toEpochMilli()),
            new End(now.minusSeconds(290).toEpochMilli()),
            new Start(now.minusSeconds(130).toEpochMilli()),
            new End(now.minusSeconds(20).toEpochMilli()),
            new Start(now.minusSeconds(10).toEpochMilli()),
            new End(now.minusSeconds(10).toEpochMilli()));

    when(backupStore.list(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                List.of(backup1, backup2, backup3, backup4, backup5, backup6, backup7)));

    when(backupStore.rangeMarkers(1)).thenReturn(CompletableFuture.completedFuture(ranges));

    // when
    actorScheduler.submitActor(backupRetention);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(10));
    actorScheduler.workUntilDone();

    // then
    verify(backupStore).delete(backup1.id());
    verify(backupStore).delete(backup2.id());
    verify(backupStore).delete(backup3.id());
    verify(backupStore).delete(backup4.id());

    verify(backupStore)
        .storeRangeMarker(
            eq(1),
            argThat(
                marker ->
                    marker.checkpointId() == backup5.id().checkpointId()
                        && marker instanceof Start));

    verify(backupStore, atLeast(1))
        .deleteRangeMarker(
            eq(1),
            argThat(
                marker ->
                    marker.checkpointId() == backup4.id().checkpointId()
                        && marker instanceof Start));
  }

  @Test
  void shouldPerformAllActionsMultiPartition() {
    // given
    reset(clusterState);
    doReturn(List.of(1, 2, 3)).when(clusterState).getPartitions();
    backupRetention =
        new BackupRetention(
            backupStore,
            new IntervalSchedule(Duration.ofSeconds(10)),
            Duration.ofMinutes(2),
            0,
            topologyManager,
            new SimpleMeterRegistry());
    final var now = actorScheduler.getClock().instant();
    final Map<Integer, List<BackupStatus>> backupsPerPartition = new HashMap<>();
    for (int i = 1; i <= 3; i++) {
      final BackupStatus backup1 = backup(i, 1, now.minusSeconds(360));
      final BackupStatus backup2 = backup(i, 1, now.minusSeconds(300));
      final BackupStatus backup3 = backup(i, 1, now.minusSeconds(290));
      final BackupStatus backup4 = backup(i, 1, now.minusSeconds(130));
      final BackupStatus backup5 = backup(i, 1, now.minusSeconds(110));
      final BackupStatus backup6 = backup(i, 1, now.minusSeconds(20));
      final BackupStatus backup7 = backup(i, 1, now.minusSeconds(10));
      backupsPerPartition.put(
          i, List.of(backup1, backup2, backup3, backup4, backup5, backup6, backup7));
    }

    final List<BackupRangeMarker> ranges =
        List.of(
            new Start(now.minusSeconds(360).toEpochMilli()),
            new End(now.minusSeconds(290).toEpochMilli()),
            new Start(now.minusSeconds(130).toEpochMilli()),
            new End(now.minusSeconds(20).toEpochMilli()),
            new Start(now.minusSeconds(10).toEpochMilli()),
            new End(now.minusSeconds(10).toEpochMilli()));

    doReturn(CompletableFuture.completedFuture(backupsPerPartition.get(1)))
        .when(backupStore)
        .list(argThat(id -> id.partitionId().get() == 1));
    doReturn(CompletableFuture.completedFuture(backupsPerPartition.get(2)))
        .when(backupStore)
        .list(argThat(id -> id.partitionId().get() == 2));
    doReturn(CompletableFuture.completedFuture(backupsPerPartition.get(3)))
        .when(backupStore)
        .list(argThat(id -> id.partitionId().get() == 3));

    when(backupStore.rangeMarkers(anyInt())).thenReturn(CompletableFuture.completedFuture(ranges));

    // when
    actorScheduler.submitActor(backupRetention);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(10));
    actorScheduler.workUntilDone();

    // then
    backupsPerPartition.forEach(
        (key, value) ->
            value
                .subList(0, 4)
                .forEach(
                    backup -> verify(backupStore).delete(argThat(id -> id.equals(backup.id())))));

    verify(backupStore)
        .storeRangeMarker(
            eq(1),
            argThat(
                marker ->
                    marker.checkpointId() == backupsPerPartition.get(1).get(4).id().checkpointId()
                        && marker instanceof Start));
    verify(backupStore)
        .storeRangeMarker(
            eq(2),
            argThat(
                marker ->
                    marker.checkpointId() == backupsPerPartition.get(2).get(4).id().checkpointId()
                        && marker instanceof Start));

    verify(backupStore)
        .storeRangeMarker(
            eq(3),
            argThat(
                marker ->
                    marker.checkpointId() == backupsPerPartition.get(3).get(4).id().checkpointId()
                        && marker instanceof Start));

    verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.getFirst())));
    verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.get(1))));

    verify(backupStore).deleteRangeMarker(eq(2), argThat(c -> c.equals(ranges.getFirst())));
    verify(backupStore).deleteRangeMarker(eq(2), argThat(c -> c.equals(ranges.get(1))));

    verify(backupStore).deleteRangeMarker(eq(3), argThat(c -> c.equals(ranges.getFirst())));
    verify(backupStore).deleteRangeMarker(eq(3), argThat(c -> c.equals(ranges.get(1))));

    verify(backupStore, atLeast(1))
        .deleteRangeMarker(
            eq(1),
            argThat(
                marker ->
                    marker.checkpointId() == backupsPerPartition.get(1).get(3).id().checkpointId()
                        && marker instanceof Start));

    verify(backupStore, atLeast(1))
        .deleteRangeMarker(
            eq(2),
            argThat(
                marker ->
                    marker.checkpointId() == backupsPerPartition.get(1).get(3).id().checkpointId()
                        && marker instanceof Start));

    verify(backupStore, atLeast(1))
        .deleteRangeMarker(
            eq(3),
            argThat(
                marker ->
                    marker.checkpointId() == backupsPerPartition.get(1).get(3).id().checkpointId()
                        && marker instanceof Start));
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
    actorScheduler.submitActor(backupRetention);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(10));
    actorScheduler.workUntilDone();

    // then
    verify(backupStore).delete(backup1.id());
    verify(backupStore).delete(backup2.id());
    verify(backupStore)
        .storeRangeMarker(
            eq(1),
            argThat(
                marker ->
                    marker.checkpointId() == backup3.id().checkpointId()
                        && marker instanceof Start));

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
    actorScheduler.submitActor(backupRetention);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(10));
    actorScheduler.workUntilDone();

    // then
    verify(backupStore).delete(backup1.id());
    verify(backupStore).delete(backup2.id());
    verify(backupStore, times(0)).storeRangeMarker(eq(1), any());

    verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.getFirst())));
    verify(backupStore).deleteRangeMarker(eq(1), argThat(c -> c.equals(ranges.get(1))));
  }

  @Test
  void shouldHandleNoBackups() {
    // given
    reset(backupStore);
    when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

    // when
    actorScheduler.submitActor(backupRetention);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(10));
    actorScheduler.workUntilDone();

    // then
    verify(backupStore).list(any());
    verify(backupStore, times(0)).deleteRangeMarker(anyInt(), any());
    verify(backupStore, times(0)).delete(any());
    verify(backupStore, times(0)).storeRangeMarker(anyInt(), any());
    verify(backupStore, times(0)).deleteRangeMarker(anyInt(), any());
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
}
