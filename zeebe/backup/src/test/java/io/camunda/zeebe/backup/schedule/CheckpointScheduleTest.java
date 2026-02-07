/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import static io.camunda.zeebe.backup.schedule.CheckpointScheduler.SKIP_CHECKPOINT_THRESHOLD;
import static io.camunda.zeebe.backup.schedule.SchedulerMetrics.LAST_CHECKPOINT_GAUGE_NAME;
import static io.camunda.zeebe.backup.schedule.SchedulerMetrics.LAST_CHECKPOINT_ID_GAUGE_NAME;
import static io.camunda.zeebe.backup.schedule.SchedulerMetrics.NEXT_CHECKPOINT_GAUGE_NAME;
import static io.camunda.zeebe.backup.schedule.SchedulerMetrics.TYPE_TAG;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

  @BeforeEach
  void setup() {
    meterRegistry.clear();
    brokerClient = mock(BrokerClient.class);
    final var topologyManager = mock(BrokerTopologyManager.class);
    final var clusterState = mock(BrokerClusterState.class);
    lenient().when(brokerClient.getTopologyManager()).thenReturn(topologyManager);
    lenient().when(topologyManager.getTopology()).thenReturn(clusterState);
    lenient().when(clusterState.getPartitionsCount()).thenReturn(1);
    lenient().when(clusterState.getPartitions()).thenReturn(List.of(1));
    backupRequestHandler = spy(new BackupRequestHandler(brokerClient));

    doAnswer(
            (ctx) -> {
              final Object backupId = ctx.getArgument(0);
              return CompletableFuture.completedFuture(backupId);
            })
        .when(backupRequestHandler)
        .takeBackup(anyLong(), any(CheckpointType.class));

    actorScheduler.getClock().reset();
    actorScheduler.getClock().pinCurrentTime();
  }

  @Test
  void shouldRequestCheckpointAtScheduledTime() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(new Schedule.IntervalSchedule(Duration.ofSeconds(5)), null);
    when(backupRequestHandler.getCheckpointState())
        .thenReturn(CompletableFuture.completedStage(checkpointState(now.toEpochMilli(), 0L)));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, times(2)).getCheckpointState();
    verify(backupRequestHandler, times(1)).checkpoint(CheckpointType.MARKER);
    verify(backupRequestHandler, times(1))
        .takeBackup(anyLong(), argThat(type -> type == CheckpointType.MARKER));
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
  void shouldRequestBackupAtScheduledTime() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(null, new Schedule.IntervalSchedule(Duration.ofSeconds(5)));

    when(backupRequestHandler.getCheckpointState())
        .thenReturn(CompletableFuture.completedStage(checkpointState(0L, now.toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, times(2)).getCheckpointState();

    verify(backupRequestHandler, times(1)).checkpoint(CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, times(1))
        .takeBackup(anyLong(), argThat(type -> type == CheckpointType.SCHEDULED_BACKUP));
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
  void shouldRequestBackupInsteadOfCheckpoint() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    final var backupInterval = Duration.ofSeconds(5).toMillis() + SKIP_CHECKPOINT_THRESHOLD;
    checkpointScheduler =
        createScheduler(
            new Schedule.IntervalSchedule(Duration.ofSeconds(5)),
            new Schedule.IntervalSchedule(Duration.ofMillis(backupInterval)));

    when(backupRequestHandler.getCheckpointState())
        .thenReturn(
            CompletableFuture.completedStage(
                checkpointState(now.toEpochMilli(), now.toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofMillis(backupInterval));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, times(2)).getCheckpointState();

    verify(backupRequestHandler, times(1)).checkpoint(CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, times(1))
        .takeBackup(anyLong(), argThat(type -> type == CheckpointType.SCHEDULED_BACKUP));
    verifyNoMoreInteractions(backupRequestHandler);
  }

  @Test
  void shouldNotProceedIfSchedulerIsStopped() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(null, new Schedule.IntervalSchedule(Duration.ofSeconds(5)));

    when(backupRequestHandler.getCheckpointState())
        .thenReturn(CompletableFuture.completedStage(checkpointState(0L, now.toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    verify(backupRequestHandler, times(1)).getCheckpointState();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    checkpointScheduler.closeAsync();
    actorScheduler.workUntilDone();

    // then

    verify(backupRequestHandler, times(0)).checkpoint(any());
    verify(backupRequestHandler, times(0)).takeBackup(anyLong(), any());
    verifyNoMoreInteractions(backupRequestHandler);
  }

  @Test
  void shouldMaintainSchedule() {
    // given
    final int iterations = 10;
    var now = actorScheduler.getClock().getCurrentTime();
    when(backupRequestHandler.getCheckpointState())
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
      when(backupRequestHandler.getCheckpointState())
          .thenReturn(CompletableFuture.completedStage(checkpointState(now.toEpochMilli(), 0L)));
    }
    // 1 for the initial plus the 10 iterations
    verify(backupRequestHandler, times(11)).getCheckpointState();

    // then
    verify(backupRequestHandler, times(iterations)).checkpoint(CheckpointType.MARKER);
    verify(backupRequestHandler, times(iterations))
        .takeBackup(anyLong(), argThat(type -> type == CheckpointType.MARKER));
    verifyNoMoreInteractions(backupRequestHandler);
  }

  @Test
  void shouldTakeDifferentCheckpointsInSuccession() {
    // given
    final var initTime = actorScheduler.getClock().getCurrentTime();
    when(backupRequestHandler.getCheckpointState())
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

    when(backupRequestHandler.getCheckpointState())
        .thenAnswer(
            invocation -> {
              final int count = callCount.incrementAndGet();
              switch (count) {
                case 1:
                  // initial
                  return CompletableFuture.completedStage(
                      checkpointState(initTime.toEpochMilli(), latestBackupCheckpoint));
                case 2:
                  // after 5s
                  return CompletableFuture.completedStage(
                      checkpointState(
                          initTime.plusSeconds(5).toEpochMilli(), latestBackupCheckpoint));
                case 3:
                  // after 10s
                  return CompletableFuture.completedStage(
                      checkpointState(
                          initTime.plusSeconds(10).toEpochMilli(), latestBackupCheckpoint));
                case 4:
                  // after 15s
                  return CompletableFuture.completedStage(
                      checkpointState(
                          initTime.plusSeconds(15).toEpochMilli(),
                          latestBackupCheckpoint + Duration.ofSeconds(15).toMillis()));
                case 5:
                  // after 20s
                  return CompletableFuture.completedStage(
                      checkpointState(
                          initTime.plusSeconds(25).toEpochMilli(),
                          latestBackupCheckpoint + Duration.ofSeconds(15).toMillis()));
                default:
                  return null;
              }
            });

    for (int i = 0; i < 4; i++) {
      actorScheduler.updateClock(Duration.ofSeconds(5));
      actorScheduler.workUntilDone();
    }
    verify(backupRequestHandler, times(5)).getCheckpointState();

    // then
    verify(backupRequestHandler, times(3)).checkpoint(CheckpointType.MARKER);
    verify(backupRequestHandler, times(3))
        .takeBackup(anyLong(), argThat(type -> type == CheckpointType.MARKER));
    verify(backupRequestHandler, times(1)).checkpoint(CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, times(1))
        .takeBackup(anyLong(), argThat(type -> type == CheckpointType.SCHEDULED_BACKUP));
    verifyNoMoreInteractions(backupRequestHandler);
  }

  @Test
  void shouldCorrectScheduleAfterUnexpectedDelay() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(null, new Schedule.IntervalSchedule(Duration.ofSeconds(5)));

    when(backupRequestHandler.getCheckpointState())
        .thenReturn(
            CompletableFuture.completedStage(
                checkpointState(0L, now.minus(1, ChronoUnit.DAYS).toEpochMilli())));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();
    actorScheduler.updateClock(Duration.ofSeconds(5));
    actorScheduler.workUntilDone();

    // then
    verify(backupRequestHandler, times(2)).getCheckpointState();
    // Schedule will be executed twice, once instantly as it's overdue and once at the corrected
    // schedule time
    verify(backupRequestHandler, times(2)).checkpoint(CheckpointType.SCHEDULED_BACKUP);
    verify(backupRequestHandler, times(2))
        .takeBackup(anyLong(), argThat(type -> type == CheckpointType.SCHEDULED_BACKUP));
    verifyNoMoreInteractions(backupRequestHandler);
  }

  @Test
  void shouldRegisterMetricsOnStartup() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(null, new Schedule.IntervalSchedule(Duration.ofSeconds(5)));

    when(backupRequestHandler.getCheckpointState())
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

    when(backupRequestHandler.getCheckpointState())
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
    when(backupRequestHandler.getCheckpointState())
        .thenReturn(
            CompletableFuture.completedStage(checkpointState(lastCheckpoint.toEpochMilli(), 0L)));

    doAnswer(
            (ctx) -> {
              return CompletableFuture.completedFuture(1L);
            })
        .when(backupRequestHandler)
        .checkpoint(any());

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone();

    // then
    // it should have triggered checkpoint immediately
    verify(backupRequestHandler).checkpoint(CheckpointType.MARKER);
  }

  @Test
  void shouldRetryOnGetCheckpointStateError() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(new Schedule.IntervalSchedule(Duration.ofSeconds(60)), null);

    // First call fails, second call succeeds
    when(backupRequestHandler.getCheckpointState())
        .thenReturn(CompletableFuture.failedStage(new RuntimeException("Expected error")))
        .thenReturn(CompletableFuture.completedStage(checkpointState(now.toEpochMilli(), 0L)));

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone(); // Should handle error and schedule backoff

    // then
    verify(backupRequestHandler, times(1)).getCheckpointState();

    // Move clock past initial backoff (1s)
    actorScheduler.updateClock(Duration.ofSeconds(2));
    actorScheduler.workUntilDone();

    // Should have retried
    verify(backupRequestHandler, times(2)).getCheckpointState();
  }

  @Test
  void shouldRetryWhenCheckpointingFailed() {
    // given
    final var now = actorScheduler.getClock().getCurrentTime();
    checkpointScheduler =
        createScheduler(new Schedule.IntervalSchedule(Duration.ofSeconds(60)), null);

    // Provide a state where last checkpoint was long ago
    when(backupRequestHandler.getCheckpointState())
        .thenReturn(
            CompletableFuture.completedStage(
                checkpointState(now.minus(Duration.ofHours(1)).toEpochMilli(), 0L)));

    // First checkpoint call fails
    doAnswer(invocation -> CompletableFuture.failedStage(new RuntimeException("Checkpoint failed")))
        .when(backupRequestHandler)
        .checkpoint(any());

    // when
    actorScheduler.submitActor(checkpointScheduler);
    actorScheduler.workUntilDone(); // Should handle checkpoint error and schedule backoff

    // then
    verify(backupRequestHandler, times(1)).checkpoint(any());

    // Move clock past initial backoff (1s)
    actorScheduler.updateClock(Duration.ofSeconds(2));
    actorScheduler.workUntilDone();

    // Should have retried (starts by acquiring state again)
    verify(backupRequestHandler, times(2)).getCheckpointState();
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
    return new CheckpointScheduler(
        checkpointSchedule, backupSchedule, backupRequestHandler, meterRegistry);
  }

  private Gauge getGauge(final String gaugeName, final CheckpointType type) {
    return meterRegistry.get(gaugeName).tag(TYPE_TAG, type.name()).gauge();
  }

  private long getGaugeValue(final String gaugeName, final CheckpointType type) {
    return (long) getGauge(gaugeName, type).value();
  }
}
