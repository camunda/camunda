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
import io.camunda.zeebe.util.collection.Tuple;
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
  private final Function<Tuple<Instant, Instant>, Tuple<Instant, CheckpointType>> checkpointDecider;
  private final Function<Tuple<Instant, Instant>, Long> executorDelayProvider;
  private boolean isStopped = false;

  public CheckpointScheduler(
      final Schedule checkpointSchedule,
      final Schedule backupSchedule,
      final BrokerClient brokerClient) {
    this.checkpointSchedule = checkpointSchedule;
    this.backupSchedule = backupSchedule;
    backupRequestHandler = new BackupRequestHandler(brokerClient);
    errorStrategy = new ExponentialBackoff(BACKOFF_MAX_DELAY_MS, BACKOFF_INITIAL_DELAY_MS, 1.2, 0);

    if (checkpointSchedule != null && backupSchedule != null) {
      checkpointDecider = this::decideForCheckpointAndBackup;
      executorDelayProvider = this::checkpointAndBackupDelayProvider;
    } else if (checkpointSchedule != null) {
      checkpointDecider = this::nextCheckpoint;
      executorDelayProvider = this::checkpointScheduleDelayProvider;
    } else {
      checkpointDecider = this::nextBackup;
      executorDelayProvider = this::backupScheduleDelayProvider;
    }
  }

  @Override
  protected void onActorStarted() {
    LOG.debug("Checkpoint scheduler started");
    isStopped = false;
    actor.run(this::reschedulingTask);
  }

  @Override
  protected void onActorCloseRequested() {
    isStopped = true;
  }

  private void reschedulingTask() {
    if (isStopped) {
      LOG.debug("Checkpoint scheduler invoked after being stopped - skipping rescheduling task");
      return;
    }

    acquireEarliestState()
        .thenCompose(
            state -> {
              LOG.debug("Acquired checkpoint state: {}", state);
              final var next = checkpointDecider.apply(state);
              final var checkpointTime = next.getLeft();
              final var checkpointType = next.getRight();
              return checkpointIfNeeded(checkpointType, checkpointTime)
                  .thenApply(
                      checkpointTaken -> {
                        if (checkpointType == CheckpointType.SCHEDULED_BACKUP) {
                          state.setRight(checkpointTime);
                          state.setLeft(checkpointTime);
                        } else {
                          state.setLeft(checkpointTime);
                        }
                        errorDelayMs = 0;
                        final var delay =
                            checkpointTaken
                                ? executorDelayProvider.apply(state)
                                : Math.abs(
                                    checkpointTime.toEpochMilli()
                                        - ActorClock.current().instant().toEpochMilli());
                        LOG.debug("Scheduling next checkpoint task in {} ms", delay);
                        return Duration.ofMillis(delay);
                      });
            })
        .exceptionally(this::onError)
        .thenAccept(delay -> actor.call(() -> actor.schedule(delay, this::reschedulingTask)));
  }

  private CompletableFuture<Boolean> checkpointIfNeeded(
      final CheckpointType type, final Instant checkpointTime) {
    final var now = ActorClock.current().instant();

    return checkpointTime.isBefore(now) || checkpointTime.equals(now)
        ? backupRequestHandler
            .checkpoint(type)
            .toCompletableFuture()
            .thenApply(
                id -> {
                  LOG.debug("Checkpoint {} triggered with id {}", type, id);
                  return true;
                })
        : CompletableFuture.completedFuture(false);
  }

  private Tuple<Instant, CheckpointType> decideForCheckpointAndBackup(
      final Tuple<Instant, Instant> state) {
    final Instant previousCheckpoint = state.getLeft();
    final Instant previousBackup = state.getRight();

    final Instant nextCheckpoint = nextExecution(checkpointSchedule, previousCheckpoint);
    final Instant nextBackup = nextExecution(backupSchedule, previousBackup);
    final long executionTimeDiff =
        Math.abs(nextCheckpoint.toEpochMilli() - nextBackup.toEpochMilli());

    LOG.debug(
        "Previous checkpoint at {}, previous backup at {}. Next checkpoint at {}, next backup at {}. Time diff {}",
        previousCheckpoint,
        previousBackup,
        nextCheckpoint,
        nextBackup,
        executionTimeDiff);

    if (executionTimeDiff <= SKIP_CHECKPOINT_THRESHOLD || nextBackup.isBefore(nextCheckpoint)) {
      // checkpoint and backup are close enough, take a full backup
      return Tuple.of(nextBackup, CheckpointType.SCHEDULED_BACKUP);
    } else {
      // checkpoint is earlier than backup, create checkpoint
      return Tuple.of(nextCheckpoint, CheckpointType.MARKER);
    }
  }

  private Tuple<Instant, CheckpointType> nextCheckpoint(final Tuple<Instant, Instant> state) {
    final Instant previousCheckpoint = state.getLeft();
    final Instant nextCheckpoint = nextExecution(checkpointSchedule, previousCheckpoint);
    return Tuple.of(nextCheckpoint, CheckpointType.MARKER);
  }

  private Tuple<Instant, CheckpointType> nextBackup(final Tuple<Instant, Instant> state) {
    final Instant previousBackup = state.getRight();
    final Instant nextBackup = nextExecution(backupSchedule, previousBackup);
    return Tuple.of(nextBackup, CheckpointType.SCHEDULED_BACKUP);
  }

  private Long checkpointAndBackupDelayProvider(final Tuple<Instant, Instant> from) {
    final var now = ActorClock.current().instant();
    final var nextBackup = nextExecution(backupSchedule, from.getRight());
    final var nextCheckpoint = nextExecution(checkpointSchedule, from.getLeft());
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

  private Long checkpointScheduleDelayProvider(final Tuple<Instant, Instant> from) {
    final var now = ActorClock.current().instant();
    final var nextCheckpoint = nextExecution(checkpointSchedule, from.getLeft());

    return nextCheckpoint.toEpochMilli() - now.toEpochMilli();
  }

  private Long backupScheduleDelayProvider(final Tuple<Instant, Instant> from) {
    final var now = ActorClock.current().instant();
    final var nextBackup = nextExecution(backupSchedule, from.getRight());

    return nextBackup.toEpochMilli() - now.toEpochMilli();
  }

  /**
   * Acquires the earliest checkpoint and backup state from all brokers.
   *
   * @return A tuple {@link Tuple} containing the earliest checkpoint timestamp(left) and earliest
   *     backup timestamp(right).
   */
  private CompletableFuture<Tuple<Instant, Instant>> acquireEarliestState() {
    return backupRequestHandler
        .getCheckpointState()
        .thenApply(
            state -> {
              final Instant minCheckpointTimestamp =
                  minFromState(state.getCheckpointStates(), checkpointSchedule);
              final Instant minBackupTimestamp =
                  minFromState(state.getBackupStates(), backupSchedule);

              return Tuple.of(minCheckpointTimestamp, minBackupTimestamp);
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
        .orElseGet(
            () -> {
              if (schedule == null) {
                return null;
              } else {
                return previousExecution(schedule, now);
              }
            });
  }

  private Duration onError(final Throwable error) {
    errorDelayMs = errorStrategy.applyAsLong(errorDelayMs);
    LOG.warn(
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
}
