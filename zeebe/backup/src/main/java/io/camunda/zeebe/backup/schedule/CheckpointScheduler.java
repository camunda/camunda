/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.admin.backup.BackupRequestHandler;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.util.ExponentialBackoff;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointScheduler extends Actor implements AutoCloseable {
  static final long SKIP_CHECKPOINT_THRESHOLD = Duration.ofSeconds(2).toMillis();
  private static final long BACKOFF_INITIAL_DELAY_MS = 1000;
  private static final long BACKOFF_MAX_DELAY_MS = 10_000;

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointScheduler.class);
  private final Schedule checkpointSchedule;
  private final Schedule backupSchedule;
  private final BackupRequestHandler backupRequestHandler;
  private long errorDelayMs;
  private final ExponentialBackoff errorStrategy;
  private final Function<CheckpointState, ScheduleInstruction> checkpointDecider;
  private final Function<CheckpointState, Long> executorDelayProvider;

  public CheckpointScheduler(
      final Schedule checkpointSchedule,
      final Schedule backupSchedule,
      final BrokerClient brokerClient) {
    this.checkpointSchedule = checkpointSchedule;
    this.backupSchedule = backupSchedule;
    backupRequestHandler = new BackupRequestHandler(brokerClient);
    errorStrategy = new ExponentialBackoff(BACKOFF_MAX_DELAY_MS, BACKOFF_INITIAL_DELAY_MS, 1.2, 0);

    checkpointDecider = determineCheckpointDeciderProvider();
    executorDelayProvider = determineDelayProvider();
  }

  @Override
  protected void onActorStarted() {
    LOG.debug("Checkpoint scheduler started");
    actor.run(this::reschedulingTask);
  }

  private void reschedulingTask() {

    acquireEarliestState()
        .thenApply(checkpointDecider)
        .thenCompose(this::checkpointIfNeeded)
        .thenCompose(this::calculateDelay)
        .thenApply(this::peekDelay)
        .thenApply(Duration::ofMillis)
        .exceptionally(this::onError)
        .thenAccept(delay -> run(() -> schedule(delay, this::reschedulingTask)));
  }

  private CompletableFuture<ScheduleInstruction> checkpointIfNeeded(
      final ScheduleInstruction instruction) {
    final var now = ActorClock.current().instant();

    return instruction.checkpointTime.isBefore(now) || instruction.checkpointTime.equals(now)
        ? backupRequestHandler
            .checkpoint(instruction.type)
            .toCompletableFuture()
            .thenApply(
                id -> {
                  LOG.debug("Checkpoint {} triggered with id {}", instruction.type, id);
                  errorDelayMs = 0;
                  return instruction.taken();
                })
        : CompletableFuture.completedFuture(instruction);
  }

  private CompletableFuture<Long> calculateDelay(final ScheduleInstruction instruction) {
    final CheckpointState currentState =
        instruction.type == CheckpointType.SCHEDULED_BACKUP
            ? new CheckpointState(instruction.checkpointTime, instruction.checkpointTime)
            : new CheckpointState(instruction.checkpointTime, instruction.state.lastBackup);

    return instruction.checkpointTaken
        ? CompletableFuture.completedFuture(executorDelayProvider.apply(currentState))
        : CompletableFuture.completedFuture(
            Math.abs(
                instruction.checkpointTime.toEpochMilli()
                    - ActorClock.current().instant().toEpochMilli()));
  }

  private Function<CheckpointState, ScheduleInstruction> determineCheckpointDeciderProvider() {
    if (checkpointSchedule != null && backupSchedule != null) {
      return this::decideForCheckpointAndBackup;
    } else if (checkpointSchedule != null) {
      return this::nextCheckpoint;
    } else {
      return this::nextBackup;
    }
  }

  private Function<CheckpointState, Long> determineDelayProvider() {
    if (checkpointSchedule != null && backupSchedule != null) {
      return this::checkpointAndBackupDelayProvider;
    } else if (checkpointSchedule != null) {
      return this::checkpointScheduleDelayProvider;
    } else {
      return this::backupScheduleDelayProvider;
    }
  }

  private ScheduleInstruction decideForCheckpointAndBackup(final CheckpointState state) {

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

    if (executionTimeDiff <= SKIP_CHECKPOINT_THRESHOLD || nextBackup.isBefore(nextCheckpoint)) {
      // checkpoint and backup are close enough, take a full backup
      return new ScheduleInstruction(CheckpointType.SCHEDULED_BACKUP, nextBackup, state);
    } else {
      // checkpoint is earlier than backup, create checkpoint
      return new ScheduleInstruction(CheckpointType.MARKER, nextCheckpoint, state);
    }
  }

  private ScheduleInstruction nextCheckpoint(final CheckpointState state) {
    final Instant nextCheckpoint = nextExecution(checkpointSchedule, state.lastCheckpoint);
    return new ScheduleInstruction(CheckpointType.MARKER, nextCheckpoint, state);
  }

  private ScheduleInstruction nextBackup(final CheckpointState state) {
    final Instant nextBackup = nextExecution(backupSchedule, state.lastBackup);
    return new ScheduleInstruction(CheckpointType.SCHEDULED_BACKUP, nextBackup, state);
  }

  private Long checkpointAndBackupDelayProvider(final CheckpointState state) {
    final var now = ActorClock.current().instant();
    final var nextBackup = nextExecution(backupSchedule, state.lastBackup);
    final var nextCheckpoint = nextExecution(checkpointSchedule, state.lastCheckpoint);
    final var decision =
        Math.abs(
            Math.min(nextBackup.toEpochMilli(), nextCheckpoint.toEpochMilli())
                - now.toEpochMilli());
    LOG.debug(
        "Next backup at {}, next checkpoint at {}: decided delay: {}",
        nextBackup,
        nextCheckpoint,
        decision);
    return decision;
  }

  private Long checkpointScheduleDelayProvider(final CheckpointState state) {
    final var now = ActorClock.current().instant();
    final var nextCheckpoint = nextExecution(checkpointSchedule, state.lastCheckpoint);

    return nextCheckpoint.toEpochMilli() - now.toEpochMilli();
  }

  private Long backupScheduleDelayProvider(final CheckpointState state) {
    final var now = ActorClock.current().instant();
    final var nextBackup = nextExecution(backupSchedule, state.lastBackup);

    return nextBackup.toEpochMilli() - now.toEpochMilli();
  }

  /**
   * Acquires the earliest checkpoint and backup state from all partitions.
   *
   * @return The current state {@link CheckpointState} containing the earliest checkpoint
   *     timestamp(left) and earliest backup timestamp(right).
   */
  private CompletableFuture<CheckpointState> acquireEarliestState() {
    return backupRequestHandler
        .getCheckpointState()
        .thenApply(
            state -> {
              final Instant minCheckpointTimestamp =
                  minFromState(state.getCheckpointStates(), checkpointSchedule);
              final Instant minBackupTimestamp =
                  minFromState(state.getBackupStates(), backupSchedule);

              return new CheckpointState(minCheckpointTimestamp, minBackupTimestamp);
            })
        .toCompletableFuture();
  }

  private Instant minFromState(
      final Set<PartitionCheckpointState> states, final Schedule schedule) {
    final var now = ActorClock.current().instant();
    return states.stream()
        .min(Comparator.comparingLong(PartitionCheckpointState::checkpointId))
        .map(PartitionCheckpointState::checkpointTimestamp)
        .map(Instant::ofEpochMilli)
        .orElseGet(() -> schedule == null ? null : previousExecution(schedule, now));
  }

  private Duration onError(final Throwable error) {
    errorDelayMs = errorStrategy.applyAsLong(errorDelayMs);
    LOG.debug(
        "Backing off checkpoint scheduling for {} due to : {}", errorDelayMs, error.getMessage());
    return Duration.ofMillis(errorDelayMs);
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

  private Long peekDelay(final long delay) {
    LOG.debug("Next checkpoint scheduled in {}ms", delay);
    return delay;
  }

  private Duration backOffOnError(final Throwable error) {
    errorDelayMs = errorStrategy.applyAsLong(errorDelayMs);
    LOG.debug(
        "Backing off checkpoint scheduling for {} due to : {}", errorDelayMs, error.getMessage());
    return Duration.ofMillis(errorDelayMs);
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
      CheckpointType type, Instant checkpointTime, CheckpointState state, boolean checkpointTaken) {
    ScheduleInstruction(
        final CheckpointType type,
        final Instant checkpointTime,
        final CheckpointState currentCheckpointState) {
      this(type, checkpointTime, currentCheckpointState, false);
    }

    ScheduleInstruction taken() {
      return new ScheduleInstruction(type, checkpointTime, state, true);
    }
  }

  record CheckpointState(Instant lastCheckpoint, Instant lastBackup) {}
}
