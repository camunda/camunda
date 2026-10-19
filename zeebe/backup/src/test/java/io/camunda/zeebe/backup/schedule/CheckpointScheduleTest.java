/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.zeebe.backup.schedule.SchedulerMetrics.LAST_CHECKPOINT_GAUGE_NAME;
import static io.camunda.zeebe.backup.schedule.SchedulerMetrics.LAST_CHECKPOINT_ID_GAUGE_NAME;
import static io.camunda.zeebe.backup.schedule.SchedulerMetrics.NEXT_CHECKPOINT_GAUGE_NAME;
import static io.camunda.zeebe.backup.schedule.SchedulerMetrics.TYPE_TAG;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionLogArchiveState;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CheckpointScheduleTest {

  private static BackupRequestHandler backupRequestHandler;
  private static BrokerClient brokerClient;

  @RegisterExtension
  public final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension();

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private CheckpointScheduler checkpointScheduler;
  private BrokerTopologyManager topologyManager;

  @BeforeEach
  void setup() {
    meterRegistry.clear();
    brokerClient = mock(BrokerClient.class);
    topologyManager = mock(BrokerTopologyManager.class);
    final var clusterState = mock(BrokerClusterState.class);
    lenient().when(brokerClient.getTopologyManager()).thenReturn(topologyManager);
    lenient()
        .when(topologyManager.getTopology(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(clusterState);
    lenient().when(clusterState.getPartitionsCount()).thenReturn(1);
    lenient().when(clusterState.getPartitions()).thenReturn(List.of(1));
    backupRequestHandler = spy(new BackupRequestHandler(brokerClient));

    doAnswer(
            (ctx) -> {
              final Object backupId = ctx.getArgument(1);
              return CompletableFuture.completedFuture(backupId);
            })
        .when(backupRequestHandler)
        .takeBackup(any(), anyLong(), any(CheckpointType.class));

    actorScheduler.getClock().reset();
    actorScheduler.getClock().pinCurrentTime();
  }

  @Test
  void shouldRequestCheckpointAtScheduledTime() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(new Schedule.IntervalSchedule(Duration.ofSeconds(5)), null);
    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedStage(checkpointState(now.toEpochMilli(), 0L)));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, times(2)).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);
    verify(backupRequestHandler, times(1))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.MARKER);
    verify(backupRequestHandler, times(1))
        .takeBackup(any(), anyLong(), argThat(type -> type == CheckpointType.MARKER));
    verifyNoMoreInteractions(backupRequestHandler);

    final var recordedTimestampValue =
        getGaugeValue(LAST_CHECKPOINT_GAUGE_NAME, CheckpointType.MARKER);
    final var recordedCheckpointIdValue =
        getGaugeValue(LAST_CHECKPOINT_ID_GAUGE_NAME, CheckpointType.MARKER);
    final var nextCheckpointTimestampValue =
        getGaugeValue(NEXT_CHECKPOINT_GAUGE_NAME, CheckpointType.MARKER);

    assertThat(recordedTimestampValue).isEqualTo(now.plusSeconds(5).toEpochMilli());
    assertThat(nextCheckpointTimestampValue).isEqualTo(now.plusSeconds(10).toEpochMilli());
    // id generation is not bound to the actor clock, so the value must be close to when we
    // initially stopped the clock
    assertThat(Instant.ofEpochMilli(recordedCheckpointIdValue))
        .isCloseTo(now, within(1, ChronoUnit.SECONDS));
  }

  @Test
  void shouldRequestCheckpointsOnlyForItsOwnPhysicalTenant() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(
            "tenant-a",
            new Schedule.IntervalSchedule(Duration.ofSeconds(5)),
            new Schedule.IntervalSchedule(Duration.ofSeconds(5)));
    when(backupRequestHandler.getCheckpointState("tenant-a"))
        .thenReturn(
            CompletableFuture.completedStage(
                checkpointState(now.toEpochMilli(), now.toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, times(1)).checkpoint("tenant-a", CheckpointType.MARKER);
    verify(backupRequestHandler, times(1)).checkpoint("tenant-a", CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, never()).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);
    verify(backupRequestHandler, never()).checkpoint(eq(DEFAULT_PHYSICAL_TENANT_ID), any());
  }

  @Test
  void shouldRequestBackupAtScheduledTime() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(null, new Schedule.IntervalSchedule(Duration.ofSeconds(5)));

    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedStage(checkpointState(0L, now.toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, times(2)).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);

    verify(backupRequestHandler, times(1))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, times(1))
        .takeBackup(any(), anyLong(), argThat(type -> type == CheckpointType.SCHEDULED_BACKUP));
    verifyNoMoreInteractions(backupRequestHandler);

    var recordedTimestampValue = getGaugeValue(LAST_CHECKPOINT_GAUGE_NAME, CheckpointType.MARKER);
    var recordedCheckpointIdValue =
        getGaugeValue(LAST_CHECKPOINT_ID_GAUGE_NAME, CheckpointType.MARKER);

    assertThat(recordedTimestampValue).isEqualTo(now.plusSeconds(5).toEpochMilli());
    assertThat(Instant.ofEpochMilli(recordedCheckpointIdValue))
        .isCloseTo(now, within(1, ChronoUnit.SECONDS));

    recordedTimestampValue =
        getGaugeValue(LAST_CHECKPOINT_GAUGE_NAME, CheckpointType.SCHEDULED_BACKUP);
    recordedCheckpointIdValue =
        getGaugeValue(LAST_CHECKPOINT_ID_GAUGE_NAME, CheckpointType.SCHEDULED_BACKUP);
    final var nextCheckpointTimestampValue =
        getGaugeValue(NEXT_CHECKPOINT_GAUGE_NAME, CheckpointType.SCHEDULED_BACKUP);
    assertThat(recordedTimestampValue).isEqualTo(now.plusSeconds(5).toEpochMilli());
    assertThat(nextCheckpointTimestampValue).isEqualTo(now.plusSeconds(10).toEpochMilli());
    assertThat(Instant.ofEpochMilli(recordedCheckpointIdValue))
        .isCloseTo(now, within(1, ChronoUnit.SECONDS));
  }

  @Test
  void shouldRunBothSchedulesIndependently() {
    // given – both schedules are enabled with the same interval
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(
            new Schedule.IntervalSchedule(Duration.ofSeconds(5)),
            new Schedule.IntervalSchedule(Duration.ofSeconds(5)));

    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(
            CompletableFuture.completedStage(
                checkpointState(now.toEpochMilli(), now.toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then – both loops fire independently
    verify(backupRequestHandler, times(1))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.MARKER);
    verify(backupRequestHandler, times(1))
        .takeBackup(any(), anyLong(), argThat(type -> type == CheckpointType.MARKER));
    verify(backupRequestHandler, times(1))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, times(1))
        .takeBackup(any(), anyLong(), argThat(type -> type == CheckpointType.SCHEDULED_BACKUP));
  }

  @Test
  void shouldNotProceedIfSchedulerIsStopped() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(null, new Schedule.IntervalSchedule(Duration.ofSeconds(5)));

    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedStage(checkpointState(0L, now.toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    verify(backupRequestHandler, times(1)).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);
    actorScheduler.updateClock(Duration.ofSeconds(5));
    checkpointScheduler.closeAsync();
    actorScheduler.workUntilDone();

    // then

    verify(backupRequestHandler, times(0)).checkpoint(eq(DEFAULT_PHYSICAL_TENANT_ID), any());
    verify(backupRequestHandler, times(0)).takeBackup(any(), anyLong(), any());
    verifyNoMoreInteractions(backupRequestHandler);
  }

  @Test
  void shouldMaintainSchedule() {
    // given
    final int iterations = 10;
    var now = actorScheduler.getClock().getCurrentTime();
    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedStage(checkpointState(now.toEpochMilli(), 0L)));
    checkpointScheduler =
        createScheduler(new Schedule.IntervalSchedule(Duration.ofSeconds(5)), null);

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    for (int i = 0; i < 10; i++) {
      actorScheduler.updateClock(Duration.ofSeconds(5));
      actorScheduler.workUntilDone();
      now = actorScheduler.getClock().getCurrentTime();
      when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
          .thenReturn(CompletableFuture.completedStage(checkpointState(now.toEpochMilli(), 0L)));
    }
    // 1 for the initial plus the 10 iterations
    verify(backupRequestHandler, times(11)).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);

    // then
    verify(backupRequestHandler, times(iterations))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.MARKER);
    verify(backupRequestHandler, times(iterations))
        .takeBackup(any(), anyLong(), argThat(type -> type == CheckpointType.MARKER));
    verifyNoMoreInteractions(backupRequestHandler);
  }

  @Test
  void shouldTakeDifferentCheckpointsInSuccession() {
    // given
    final var initTime = actorScheduler.getClock().getCurrentTime();
    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(
            CompletableFuture.completedStage(
                checkpointState(initTime.toEpochMilli(), initTime.toEpochMilli())));
    checkpointScheduler =
        createScheduler(
            new Schedule.IntervalSchedule(Duration.ofSeconds(5)),
            new Schedule.IntervalSchedule(Duration.ofSeconds(15)));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    final var latestBackupCheckpoint = initTime.toEpochMilli();
    final AtomicInteger callCount = new AtomicInteger(0);

    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenAnswer(
            invocation -> {
              final int count = callCount.incrementAndGet();
              switch (count) {
                case 1:
                  // marker loop: initial state
                  return CompletableFuture.completedStage(
                      checkpointState(initTime.toEpochMilli(), latestBackupCheckpoint));
                case 2:
                  // backup loop: initial state
                  return CompletableFuture.completedStage(
                      checkpointState(initTime.toEpochMilli(), latestBackupCheckpoint));
                case 3:
                  // marker loop after 5s
                  return CompletableFuture.completedStage(
                      checkpointState(
                          initTime.plusSeconds(5).toEpochMilli(), latestBackupCheckpoint));
                case 4:
                  // backup loop after 5s (not due yet)
                  return CompletableFuture.completedStage(
                      checkpointState(
                          initTime.plusSeconds(5).toEpochMilli(), latestBackupCheckpoint));
                case 5:
                  // marker loop after 10s
                  return CompletableFuture.completedStage(
                      checkpointState(
                          initTime.plusSeconds(10).toEpochMilli(), latestBackupCheckpoint));
                case 6:
                  // backup loop after 10s (not due yet)
                  return CompletableFuture.completedStage(
                      checkpointState(
                          initTime.plusSeconds(10).toEpochMilli(), latestBackupCheckpoint));
                case 7:
                  // marker loop after 15s
                  return CompletableFuture.completedStage(
                      checkpointState(
                          initTime.plusSeconds(15).toEpochMilli(), latestBackupCheckpoint));
                case 8:
                  // backup loop after 15s (due now)
                  return CompletableFuture.completedStage(
                      checkpointState(
                          initTime.plusSeconds(15).toEpochMilli(), latestBackupCheckpoint));
                default:
                  return CompletableFuture.completedStage(
                      checkpointState(
                          initTime.plusSeconds(20).toEpochMilli(),
                          latestBackupCheckpoint + Duration.ofSeconds(15).toMillis()));
              }
            });

    for (int i = 0; i < 3; i++) {
      actorScheduler.updateClock(Duration.ofSeconds(5));
      actorScheduler.workUntilDone();
    }

    // then – marker fires every 5s (3 times), backup fires at 15s (1 time)
    verify(backupRequestHandler, times(3))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.MARKER);
    verify(backupRequestHandler, times(3))
        .takeBackup(any(), anyLong(), argThat(type -> type == CheckpointType.MARKER));
    verify(backupRequestHandler, times(1))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, times(1))
        .takeBackup(any(), anyLong(), argThat(type -> type == CheckpointType.SCHEDULED_BACKUP));
  }

  @Test
  void shouldCorrectScheduleAfterUnexpectedDelay() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(null, new Schedule.IntervalSchedule(Duration.ofSeconds(5)));

    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(
            CompletableFuture.completedStage(
                checkpointState(0L, now.minus(1, ChronoUnit.DAYS).toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, times(2)).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);
    // Schedule will be executed twice, once instantly as it's overdue and once at the corrected
    // schedule time
    verify(backupRequestHandler, times(2))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, times(2))
        .takeBackup(any(), anyLong(), argThat(type -> type == CheckpointType.SCHEDULED_BACKUP));
    verifyNoMoreInteractions(backupRequestHandler);
  }

  @Test
  void shouldRegisterMetricsOnStartup() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(null, new Schedule.IntervalSchedule(Duration.ofSeconds(5)));

    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(
            CompletableFuture.completedStage(
                checkpointState(0L, now.minus(1, ChronoUnit.DAYS).toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();

    // then
    assertThat(getGauge(LAST_CHECKPOINT_GAUGE_NAME, CheckpointType.MARKER)).isNotNull();
    assertThat(getGauge(LAST_CHECKPOINT_ID_GAUGE_NAME, CheckpointType.MARKER)).isNotNull();
    assertThat(getGauge(NEXT_CHECKPOINT_GAUGE_NAME, CheckpointType.MARKER)).isNotNull();
    assertThat(getGauge(LAST_CHECKPOINT_GAUGE_NAME, CheckpointType.SCHEDULED_BACKUP)).isNotNull();
    assertThat(getGauge(LAST_CHECKPOINT_ID_GAUGE_NAME, CheckpointType.SCHEDULED_BACKUP))
        .isNotNull();
    assertThat(getGauge(NEXT_CHECKPOINT_GAUGE_NAME, CheckpointType.SCHEDULED_BACKUP)).isNotNull();
  }

  @Test
  void shouldRemoveMetricsOnShutdown() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(null, new Schedule.IntervalSchedule(Duration.ofSeconds(5)));

    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(
            CompletableFuture.completedStage(
                checkpointState(0L, now.minus(1, ChronoUnit.DAYS).toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    assertThat(meterRegistry.getMeters()).isNotEmpty();
    checkpointScheduler.closeAsync();
    actorScheduler.workUntilDone();

    // then
    assertThat(meterRegistry.getMeters()).isEmpty();
  }

  @Test
  void shouldNotDelayWhenScheduledCheckpointWasAlreadyMissed() {
    // given
    final var interval = Duration.ofMinutes(1);
    final var now = actorScheduler.getClock().getCurrentTime();
    // checkpoint was scheduled 10 seconds ago
    final var lastCheckpoint = now.minus(interval).minus(Duration.ofSeconds(10));

    checkpointScheduler = createScheduler(new Schedule.IntervalSchedule(interval), null);
    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(
            CompletableFuture.completedStage(checkpointState(lastCheckpoint.toEpochMilli(), 0L)));

    doAnswer(
            (ctx) -> {
              return CompletableFuture.completedFuture(1L);
            })
        .when(backupRequestHandler)
        .checkpoint(eq(DEFAULT_PHYSICAL_TENANT_ID), any());

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();

    // then
    // it should have triggered checkpoint immediately
    verify(backupRequestHandler).checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.MARKER);
  }

  @Test
  void shouldRetryOnGetCheckpointStateError() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(new Schedule.IntervalSchedule(Duration.ofSeconds(60)), null);

    // First call fails, second call succeeds
    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.failedStage(new RuntimeException("Expected error")))
        .thenReturn(CompletableFuture.completedStage(checkpointState(now.toEpochMilli(), 0L)));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone(); // Should handle error and schedule backoff

    // then
    verify(backupRequestHandler, times(1)).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);

    // Move clock past initial backoff (1s)
    actorScheduler.updateClock(Duration.ofSeconds(2));
    actorScheduler.workUntilDone();

    // Should have retried
    verify(backupRequestHandler, times(2)).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldRetryWhenCheckpointingFailed() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(new Schedule.IntervalSchedule(Duration.ofSeconds(60)), null);

    // Provide a state where last checkpoint was long ago
    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(
            CompletableFuture.completedStage(
                checkpointState(now.minus(Duration.ofHours(1)).toEpochMilli(), 0L)));

    // First checkpoint call fails
    doAnswer(invocation -> CompletableFuture.failedStage(new RuntimeException("Checkpoint failed")))
        .when(backupRequestHandler)
        .checkpoint(eq(DEFAULT_PHYSICAL_TENANT_ID), any());

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone(); // Should handle checkpoint error and schedule backoff

    // then
    verify(backupRequestHandler, times(1)).checkpoint(eq(DEFAULT_PHYSICAL_TENANT_ID), any());

    // Move clock past initial backoff (1s)
    actorScheduler.updateClock(Duration.ofSeconds(2));
    actorScheduler.workUntilDone();

    // Should have retried (starts by acquiring state again)
    verify(backupRequestHandler, times(2)).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldNotSendAnyRequestWhileTenantIsRecovering() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(
            new Schedule.IntervalSchedule(Duration.ofSeconds(5)),
            new Schedule.IntervalSchedule(Duration.ofSeconds(5)));
    when(topologyManager.isRecovering(DEFAULT_PHYSICAL_TENANT_ID)).thenReturn(true);
    lenient()
        .when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(
            CompletableFuture.completedStage(
                checkpointState(now.toEpochMilli(), now.toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then — not even the state query is sent, so a recovering tenant sees no traffic at all
    verify(backupRequestHandler, never()).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);
    verify(backupRequestHandler, never()).checkpoint(eq(DEFAULT_PHYSICAL_TENANT_ID), any());
  }

  @Test
  void shouldKeepTickingWhileTenantIsRecovering() {
    // given — recovery ends after the first two ticks were skipped
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(new Schedule.IntervalSchedule(Duration.ofSeconds(5)), null);
    when(topologyManager.isRecovering(DEFAULT_PHYSICAL_TENANT_ID)).thenReturn(true, true, false);
    when(backupRequestHandler.getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedStage(checkpointState(now.toEpochMilli(), 0L)));

    // when — the initial tick and the one 5s later are skipped
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();
    verify(backupRequestHandler, never()).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);

    // when — the tick after that finds the tenant back in processing mode
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then — the loop was still ticking on its own schedule, so it resumes without a restart
    verify(backupRequestHandler, times(1)).getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);
    verify(backupRequestHandler, times(1))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.MARKER);
  }

  @Test
  void shouldNotActivateLogArchiveWhileABrokerIsNotUpgraded() {
    // given
    givenBrokerVersions(VersionUtil.getVersion(), "0.1.0");
    checkpointScheduler = createLogArchiveActivationScheduler();

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofMinutes(1));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, never()).activateLogArchive(any());
  }

  @Test
  void shouldActivateLogArchiveUntilEveryTimelineIsStarted() {
    // given
    givenBrokerVersions(VersionUtil.getVersion(), VersionUtil.getVersion());
    doReturn(CompletableFuture.completedFuture(Map.of(1, true, 2, false)))
        .doReturn(CompletableFuture.completedFuture(Map.of(1, true, 2, true)))
        .when(backupRequestHandler)
        .activateLogArchive(DEFAULT_PHYSICAL_TENANT_ID);
    checkpointScheduler = createLogArchiveActivationScheduler();

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    for (int i = 0; i < 10; i++) {
      actorScheduler.updateClock(Duration.ofSeconds(10));
      actorScheduler.workUntilDone();
    }

    // then - activated again until both partitions report a started timeline, then stopped
    verify(backupRequestHandler, times(2)).activateLogArchive(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldActivateLogArchiveOfAPartitionAddedLater() {
    // given - every timeline is started, then a scale-up adds partition 2
    givenBrokerVersions(VersionUtil.getVersion());
    doReturn(CompletableFuture.completedFuture(Map.of(1, true)))
        .doReturn(CompletableFuture.completedFuture(Map.of(1, true, 2, true)))
        .when(backupRequestHandler)
        .activateLogArchive(DEFAULT_PHYSICAL_TENANT_ID);
    checkpointScheduler = createLogArchiveActivationScheduler();
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    for (int i = 0; i < 3; i++) {
      actorScheduler.updateClock(Duration.ofSeconds(10));
      actorScheduler.workUntilDone();
    }
    verify(backupRequestHandler, times(1)).activateLogArchive(DEFAULT_PHYSICAL_TENANT_ID);

    // when
    final var topology = topologyManager.getTopology(DEFAULT_PHYSICAL_TENANT_ID);
    when(topology.getPartitionsCount()).thenReturn(2);
    when(topology.getPartitions()).thenReturn(List.of(1, 2));
    for (int i = 0; i < 6; i++) {
      actorScheduler.updateClock(Duration.ofSeconds(10));
      actorScheduler.workUntilDone();
    }

    // then - activated once more, then idle again
    verify(backupRequestHandler, times(2)).activateLogArchive(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldNotActivateLogArchiveWhenDisabled() {
    // given
    givenBrokerVersions(VersionUtil.getVersion());
    checkpointScheduler = createScheduler(null, null);

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofMinutes(1));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, never()).activateLogArchive(any());
  }

  @Test
  void shouldTakeHandoverBackupWhenLogArchiveIsReady() {
    // given - the archive passed the last backup and archived a snapshot
    final var now = actorScheduler.getClock().getCurrentTime().toEpochMilli();
    final var state = backupState(now, 1L, CheckpointType.SCHEDULED_BACKUP, 50);
    state.setLogArchiveStates(Set.of(new PartitionLogArchiveState(1, true, 60, 10, -1)));
    givenCheckpointState(state);
    checkpointScheduler = createHandoverScheduler();

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, times(1))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.HANDOVER);
    verify(backupRequestHandler, never())
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, never()).completeHandover(any(), anyLong());
  }

  @Test
  void shouldCompleteHandoverWithoutTakingBackupsWhileItIsPending() {
    // given - H is confirmed and archived, but not recorded yet
    final var now = actorScheduler.getClock().getCurrentTime().toEpochMilli();
    final var state = backupState(now, 7L, CheckpointType.HANDOVER, 50);
    state.setLogArchiveStates(Set.of(new PartitionLogArchiveState(1, true, 60, 10, -1)));
    givenCheckpointState(state);
    doReturn(CompletableFuture.completedFuture(null))
        .when(backupRequestHandler)
        .completeHandover(DEFAULT_PHYSICAL_TENANT_ID, 7L);
    checkpointScheduler = createHandoverScheduler();

    // when - well past the next scheduled backup
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    for (int i = 0; i < 6; i++) {
      actorScheduler.updateClock(Duration.ofSeconds(5));
      actorScheduler.workUntilDone();
    }

    // then - sent right away and retried, but no backup that could hide H
    verify(backupRequestHandler, atLeast(3)).completeHandover(DEFAULT_PHYSICAL_TENANT_ID, 7L);
    verify(backupRequestHandler, never()).checkpoint(eq(DEFAULT_PHYSICAL_TENANT_ID), any());
  }

  @Test
  void shouldTakeScheduledBackupsAfterTheHandover() {
    // given - the handover is first pending, then recorded
    final var now = actorScheduler.getClock().getCurrentTime().toEpochMilli();
    final var pending = backupState(now, 7L, CheckpointType.HANDOVER, 50);
    pending.setLogArchiveStates(Set.of(new PartitionLogArchiveState(1, true, 60, 10, -1)));
    final var recorded = backupState(now, 7L, CheckpointType.HANDOVER, 50);
    recorded.setLogArchiveStates(Set.of(new PartitionLogArchiveState(1, true, 60, 10, 7L)));
    doReturn(CompletableFuture.completedStage(pending))
        .doReturn(CompletableFuture.completedStage(recorded))
        .when(backupRequestHandler)
        .getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);
    doReturn(CompletableFuture.completedFuture(null))
        .when(backupRequestHandler)
        .completeHandover(DEFAULT_PHYSICAL_TENANT_ID, 7L);
    checkpointScheduler = createHandoverScheduler();

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, times(1)).completeHandover(DEFAULT_PHYSICAL_TENANT_ID, 7L);
    verify(backupRequestHandler, times(1))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, never())
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.HANDOVER);
  }

  @Test
  void shouldKeepTakingBackupsWhileAPartitionAddedAfterTheHandoverRecordsIt() {
    // given - partition 1 recorded H, partition 2 was added later and archived from its start
    final var now = actorScheduler.getClock().getCurrentTime().toEpochMilli();
    final var state = backupState(now, 7L, CheckpointType.HANDOVER, 50);
    state.setLogArchiveStates(
        Set.of(
            new PartitionLogArchiveState(1, true, 60, 10, 7L),
            new PartitionLogArchiveState(2, true, 30, 5, -1, 1)));
    givenCheckpointState(state);
    doReturn(CompletableFuture.completedFuture(null))
        .when(backupRequestHandler)
        .completeHandover(DEFAULT_PHYSICAL_TENANT_ID, 7L);
    checkpointScheduler = createHandoverScheduler();

    // when - past the next scheduled backup
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    for (int i = 0; i < 6; i++) {
      actorScheduler.updateClock(Duration.ofSeconds(5));
      actorScheduler.workUntilDone();
    }

    // then - the handover is sent and retried, and the scheduled backup is taken meanwhile
    verify(backupRequestHandler, atLeast(3)).completeHandover(DEFAULT_PHYSICAL_TENANT_ID, 7L);
    verify(backupRequestHandler, atLeast(1))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, never())
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.HANDOVER);
  }

  @Test
  void shouldIgnoreLogArchiveStateWhenLogArchiveIsDisabled() {
    // given - a state that would lead to a handover backup with the archive enabled
    final var now = actorScheduler.getClock().getCurrentTime().toEpochMilli();
    final var state = backupState(now, 1L, CheckpointType.SCHEDULED_BACKUP, 50);
    state.setLogArchiveStates(Set.of(new PartitionLogArchiveState(1, true, 60, 10, -1)));
    givenCheckpointState(state);
    checkpointScheduler =
        createScheduler(null, new Schedule.IntervalSchedule(Duration.ofSeconds(5)));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, times(1))
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, never())
        .checkpoint(DEFAULT_PHYSICAL_TENANT_ID, CheckpointType.HANDOVER);
  }

  private void givenCheckpointState(final CheckpointStateResponse state) {
    doReturn(CompletableFuture.completedStage(state))
        .when(backupRequestHandler)
        .getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID);
  }

  private CheckpointStateResponse backupState(
      final long timestamp, final long backupId, final CheckpointType type, final long position) {
    final var response = new CheckpointStateResponse();
    response.setBackupStates(
        Set.of(new PartitionCheckpointState(1, backupId, type, timestamp, position, 1)));
    return response;
  }

  /** A scheduler with a 5s backup schedule and the log archive enabled, its activation done. */
  private CheckpointScheduler createHandoverScheduler() {
    givenBrokerVersions(VersionUtil.getVersion());
    doReturn(CompletableFuture.completedFuture(Map.of(1, true)))
        .when(backupRequestHandler)
        .activateLogArchive(DEFAULT_PHYSICAL_TENANT_ID);
    return new CheckpointScheduler(
        DEFAULT_PHYSICAL_TENANT_ID,
        null,
        new Schedule.IntervalSchedule(Duration.ofSeconds(5)),
        backupRequestHandler,
        topologyManager,
        meterRegistry,
        true);
  }

  private void givenBrokerVersions(final String... versions) {
    final var topology = topologyManager.getTopology(DEFAULT_PHYSICAL_TENANT_ID);
    final var brokers = new ArrayList<BrokerMemberId>();
    for (int i = 0; i < versions.length; i++) {
      final var broker = BrokerMemberId.from(i);
      brokers.add(broker);
      when(topology.getBrokerVersion(broker)).thenReturn(versions[i]);
    }
    when(topology.isInitialized()).thenReturn(true);
    when(topology.getClusterSize()).thenReturn(versions.length);
    when(topology.getBrokers()).thenReturn(brokers);
  }

  private CheckpointScheduler createLogArchiveActivationScheduler() {
    return new CheckpointScheduler(
        DEFAULT_PHYSICAL_TENANT_ID,
        null,
        null,
        backupRequestHandler,
        topologyManager,
        meterRegistry,
        true);
  }

  private CheckpointStateResponse checkpointState(
      final long checkpointTimestamp, final long backupTimestamp) {
    final var response = new CheckpointStateResponse();
    if (checkpointTimestamp > 0) {
      final var partitionState =
          new PartitionCheckpointState(
              1, checkpointTimestamp, CheckpointType.MARKER, checkpointTimestamp, -1);

      response.setCheckpointStates(Set.of(partitionState));
    }
    if (backupTimestamp > 0) {
      final var partitionState =
          new PartitionCheckpointState(
              1, backupTimestamp, CheckpointType.SCHEDULED_BACKUP, backupTimestamp, -1);

      response.setBackupStates(Set.of(partitionState));
    }
    return response;
  }

  private CheckpointScheduler createScheduler(
      final Schedule checkpointSchedule, final Schedule backupSchedule) {
    return createScheduler(DEFAULT_PHYSICAL_TENANT_ID, checkpointSchedule, backupSchedule);
  }

  private CheckpointScheduler createScheduler(
      final String physicalTenantId,
      final Schedule checkpointSchedule,
      final Schedule backupSchedule) {
    return new CheckpointScheduler(
        physicalTenantId,
        checkpointSchedule,
        backupSchedule,
        backupRequestHandler,
        topologyManager,
        meterRegistry);
  }

  private Gauge getGauge(final String gaugeName, final CheckpointType type) {
    return meterRegistry.get(gaugeName).tag(TYPE_TAG, type.name()).gauge();
  }

  private long getGaugeValue(final String gaugeName, final CheckpointType type) {
    return (long) getGauge(gaugeName, type).value();
  }
}
