/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import static io.camunda.zeebe.backup.schedule.CheckpointScheduler.SKIP_CHECKPOINT_THRESHOLD;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Duration;
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

  private CheckpointScheduler checkpointScheduler;

  @BeforeEach
  void setup() {
    backupRequestHandler = mock(BackupRequestHandler.class);
    brokerClient = mock(BrokerClient.class);
    final var topologyManager = mock(BrokerTopologyManager.class);
    final var clusterState = mock(BrokerClusterState.class);
    lenient().when(brokerClient.getTopologyManager()).thenReturn(topologyManager);
    lenient().when(topologyManager.getTopology()).thenReturn(clusterState);
    lenient().when(clusterState.getPartitionsCount()).thenReturn(1);
    lenient().when(clusterState.getPartitions()).thenReturn(List.of(1));

    when(backupRequestHandler.checkpoint(any())).thenCallRealMethod();

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
    try {
      final var scheduler =
          new CheckpointScheduler(
              checkpointSchedule, backupSchedule, brokerClient, new SimpleMeterRegistry());
      final Field checkpointCreatorField =
          CheckpointScheduler.class.getDeclaredField("backupRequestHandler");
      checkpointCreatorField.setAccessible(true);
      // inject mocked backup request handler
      checkpointCreatorField.set(scheduler, backupRequestHandler);

      return scheduler;
    } catch (final NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
