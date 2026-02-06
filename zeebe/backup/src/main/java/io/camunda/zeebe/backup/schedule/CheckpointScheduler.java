/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.ExponentialBackoff;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointScheduler extends Actor implements AutoCloseable {

  // If the time difference between the next checkpoint and backup is less than this threshold, we
  // skip the checkpoint and instruct only a backup on the backup's defined interval. Since backups
  // also create a checkpoint, this avoids  redundant checkpoints and the possibility for
  // checkpointId collisions.
  static final long SKIP_CHECKPOINT_THRESHOLD = Duration.ofSeconds(2).toMillis();
  private static final long BACKOFF_INITIAL_DELAY_MS = 1000;
  private static final long BACKOFF_MAX_DELAY_MS = 10_000;

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointScheduler.class);
  private final Schedule checkpointSchedule;
  private final Schedule backupSchedule;
  private final BackupRequestHandler backupRequestHandler;
  private long errorDelayMs;
  private final ExponentialBackoff errorStrategy;
  private final SchedulerMetrics metrics;

  public CheckpointScheduler(
      final Schedule checkpointSchedule,
      final Schedule backupSchedule,
      final BackupRequestHandler backupRequestHandler,
      final MeterRegistry meterRegistry) {
    this.checkpointSchedule = checkpointSchedule;
    this.backupSchedule = backupSchedule;
    metrics = new SchedulerMetrics(meterRegistry);
    this.backupRequestHandler = backupRequestHandler;
    errorStrategy = new ExponentialBackoff(BACKOFF_MAX_DELAY_MS, BACKOFF_INITIAL_DELAY_MS, 1.2, 0);
  }

  @Override
  protected void onActorStarted() {
    LOG.debug("Checkpoint scheduler started");
    metrics.register();
    actor.run(this::reschedulingTask);
  }

  @Override
  protected void onActorClosed() {
    LOG.debug("Checkpoint scheduler stopped");
    metrics.close();
  }

  private void reschedulingTask() {
    acquireEarliestState()
        .thenApply(this::determineCheckpoint, this)
        .andThen(this::checkpointIfNeeded, this)
        .onComplete(
            (instruction, error) -> {
              final long delay;
              if (error != null) {
                delay = backOffOnError(error);
              } else {
                errorDelayMs = 0;
                delay = calculateDelay(instruction);
                if (instruction.checkpointTaken) {
                  metrics.recordLastCheckpointId(instruction.checkpointId, instruction.type);
                  metrics.recordLastCheckpointTime(instruction.checkpointTime, instruction.type);
                }
              }
              LOG.debug("Next checkpoint scheduled in {} ms", delay);
              schedule(Duration.ofMillis(delay), this::reschedulingTask);
            },
            this);
  }

  private CompletableActorFuture<ScheduleInstruction> checkpointIfNeeded(
      final ScheduleInstruction instruction) {
    final var now = ActorClock.currentInstant();
    final CompletableActorFuture<ScheduleInstruction> future = new CompletableActorFuture<>();
    if (instruction.checkpointTime.isBefore(now) || instruction.checkpointTime.equals(now)) {
      backupRequestHandler
          .checkpoint(instruction.type)
          .toCompletableFuture()
          .thenApplyAsync(
              id -> {
                LOG.debug("Checkpoint {} triggered with id {}", instruction.type, id);
                final var currentClock = ActorClock.currentInstant();
                // The instructions checkpoint timestamp might be in the past leading to an
                // instant execution of the schedule. However, for the next interval we want to
                // readjust the schedule to account for that drift.
                return instruction.taken(id, currentClock);
              },
              this)
          .whenCompleteAsync(future, this);
    } else {
      future.complete(instruction);
    }
    return future;
  }

  private long calculateDelay(final ScheduleInstruction instruction) {
    final CheckpointState currentState =
        instruction.type == CheckpointType.SCHEDULED_BACKUP
            ? new CheckpointState(instruction.checkpointTime, instruction.checkpointTime)
            : new CheckpointState(instruction.checkpointTime, instruction.state.lastBackup);

    final var delay =
        instruction.checkpointTaken
            ? determineDelayFromSchedules(currentState)
            : instruction.checkpointTime.toEpochMilli() - ActorClock.currentTimeMillis();

    return Math.max(0, delay);
  }

  /**
   * Acquires the earliest checkpoint and backup state from all partitions.
   *
   * @return The current state {@link CheckpointState} containing the earliest checkpoint
   *     timestamp(left) and earliest backup timestamp(right).
   */
  private ActorFuture<CheckpointState> acquireEarliestState() {
    final ActorFuture<CheckpointState> future = createFuture();
    backupRequestHandler
        .getCheckpointState()
        .thenApplyAsync(
            response -> {
              final Instant minCheckpointTimestamp =
                  minFromState(response.getCheckpointStates(), checkpointSchedule);
              final Instant minBackupTimestamp =
                  minFromState(response.getBackupStates(), backupSchedule);
              return new CheckpointState(minCheckpointTimestamp, minBackupTimestamp);
            },
            this)
        .whenCompleteAsync(future, this);
    return future;
  }

  private Instant minFromState(
      final Set<PartitionCheckpointState> states, final Schedule schedule) {
    final var now = ActorClock.currentInstant();
    return states.stream()
        .min(Comparator.comparingLong(PartitionCheckpointState::checkpointId))
        .map(PartitionCheckpointState::checkpointTimestamp)
        .map(Instant::ofEpochMilli)
        .orElseGet(() -> schedule == null ? null : previousExecution(schedule, now));
  }

  private ScheduleInstruction determineCheckpoint(final CheckpointState state) {
    if (backupSchedule != null && checkpointSchedule != null) {
      final Instant nextCheckpoint = nextExecution(checkpointSchedule, state.lastCheckpoint);
      final Instant nextBackup = nextExecution(backupSchedule, state.lastBackup);
      final long executionTimeDiff =
          Math.abs(nextCheckpoint.toEpochMilli() - nextBackup.toEpochMilli());

      LOG.debug(
          "Previous checkpoint at {}, previous backup at {}. Next checkpoint at {}, next backup at {}. Time diff {}",
          state.lastCheckpoint,
          state.lastBackup,
          nextCheckpoint,
          nextBackup,
          executionTimeDiff);

      return executionTimeDiff <= SKIP_CHECKPOINT_THRESHOLD || nextBackup.isBefore(nextCheckpoint)
          // checkpoint and backup are close enough, take a full backup
          ? new ScheduleInstruction(CheckpointType.SCHEDULED_BACKUP, nextBackup, state)
          : new ScheduleInstruction(CheckpointType.MARKER, nextCheckpoint, state);
    } else if (checkpointSchedule != null) {
      final Instant nextCheckpoint = nextExecution(checkpointSchedule, state.lastCheckpoint);
      return new ScheduleInstruction(CheckpointType.MARKER, nextCheckpoint, state);
    } else {
      final Instant nextBackup = nextExecution(backupSchedule, state.lastBackup);
      return new ScheduleInstruction(CheckpointType.SCHEDULED_BACKUP, nextBackup, state);
    }
  }

  private long determineDelayFromSchedules(final CheckpointState state) {
    final var now = ActorClock.currentTimeMillis();
    final Instant next;

    if (backupSchedule != null && checkpointSchedule != null) {
      final var nextBackup = nextExecution(backupSchedule, state.lastBackup);
      final var nextCheckpoint = nextExecution(checkpointSchedule, state.lastCheckpoint);
      metrics.recordNextExecution(nextBackup, CheckpointType.SCHEDULED_BACKUP);
      metrics.recordNextExecution(nextCheckpoint, CheckpointType.MARKER);
      next = nextBackup.isBefore(nextCheckpoint) ? nextBackup : nextCheckpoint;
    } else if (checkpointSchedule != null) {
      next = nextExecution(checkpointSchedule, state.lastCheckpoint);
      metrics.recordNextExecution(next, CheckpointType.MARKER);
    } else {
      next = nextExecution(backupSchedule, state.lastBackup);
      metrics.recordNextExecution(next, CheckpointType.SCHEDULED_BACKUP);
    }
    return next.toEpochMilli() - now;
  }

  private long backOffOnError(final Throwable error) {
    errorDelayMs = errorStrategy.applyAsLong(errorDelayMs);
    LOG.debug(
        "Backing off checkpoint scheduling for {} due to : {}", errorDelayMs, error.getMessage());
    return errorDelayMs;
  }

  private Instant nextExecution(final Schedule schedule, final Instant from) {
    return schedule
        .nextExecution(from)
        .orElseThrow(() -> new CouldNotDetermineNextExecution(schedule));
  }

  private Instant previousExecution(final Schedule schedule, final Instant from) {
    return schedule
        .previousExecution(from)
        .orElseThrow(() -> new CouldNotDetermineNextExecution(schedule));
  }

  static class CouldNotDetermineNextExecution extends RuntimeException {
    public CouldNotDetermineNextExecution(final Schedule schedule) {
      super("Could not determine next execution time for schedule: %s".formatted(schedule));
    }
  }

  record ScheduleInstruction(
      long checkpointId,
      CheckpointType type,
      Instant checkpointTime,
      CheckpointState state,
      boolean checkpointTaken) {
    ScheduleInstruction(
        final CheckpointType type,
        final Instant checkpointTime,
        final CheckpointState currentCheckpointState) {
      this(0L, type, checkpointTime, currentCheckpointState, false);
    }

    ScheduleInstruction taken(final long checkpointId, final Instant checkpointTime) {
      return new ScheduleInstruction(checkpointId, type, checkpointTime, state, true);
    }
  }

  record CheckpointState(Instant lastCheckpoint, Instant lastBackup) {}
}
