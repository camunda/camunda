/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
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
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointScheduler extends Actor implements AutoCloseable {

  private static final double LAGGING_PARTITION_FACTOR = 1.5;
  private static final long BACKOFF_INITIAL_DELAY_MS = 1000;
  private static final long BACKOFF_MAX_DELAY_MS = 10_000;

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointScheduler.class);
  private final Schedule checkpointSchedule;
  private final Schedule backupSchedule;
  private final BackupRequestHandler backupRequestHandler;
  private final SchedulerMetrics metrics;

  private long markerErrorDelayMs;
  private final ExponentialBackoff markerErrorStrategy;
  private long backupErrorDelayMs;
  private final ExponentialBackoff backupErrorStrategy;

  public CheckpointScheduler(
      final Schedule checkpointSchedule,
      final Schedule backupSchedule,
      final BackupRequestHandler backupRequestHandler,
      final MeterRegistry meterRegistry) {
    this.checkpointSchedule = checkpointSchedule;
    this.backupSchedule = backupSchedule;
    metrics = new SchedulerMetrics(meterRegistry);
    this.backupRequestHandler = backupRequestHandler;
    markerErrorStrategy =
        new ExponentialBackoff(BACKOFF_MAX_DELAY_MS, BACKOFF_INITIAL_DELAY_MS, 1.2, 0);
    backupErrorStrategy =
        new ExponentialBackoff(BACKOFF_MAX_DELAY_MS, BACKOFF_INITIAL_DELAY_MS, 1.2, 0);
  }

  @Override
  protected void onActorStarted() {
    LOG.debug("Checkpoint scheduler started");
    metrics.register();
    if (checkpointSchedule != null) {
      actor.run(this::markerSchedulingTask);
    }
    if (backupSchedule != null) {
      actor.run(this::backupSchedulingTask);
    }
  }

  @Override
  protected void onActorClosed() {
    LOG.debug("Checkpoint scheduler stopped");
    metrics.close();
  }

  private void markerSchedulingTask() {
    acquirePartitionStates(CheckpointStateResponse::getCheckpointStates)
        .thenApply(this::determineNextCheckpoint, this)
        .andThen(this::markerCheckpoint, this)
        .onComplete(this::handleExecutionCompletion, this);
  }

  private void backupSchedulingTask() {
    acquirePartitionStates(CheckpointStateResponse::getBackupStates)
        .thenApply(this::determineNextBackup, this)
        .andThen(this::backupCheckpoint, this)
        .onComplete(this::handleExecutionCompletion, this);
  }

  /**
   * Fetches the current checkpoint state from the {@link BackupRequestHandler} and extracts the
   * relevant partition states using the supplied filter (checkpoint states for the marker loop,
   * backup states for the backup loop).
   *
   * <p>The response is mapped and completed on the actor thread ({@code this}) so downstream
   * processing of the returned future is always single-threaded.
   *
   * @param stateFilter projection from the full response to the subset of partition states needed
   *     by the calling loop
   * @return a future that completes with the set of partition checkpoint states
   */
  private ActorFuture<Set<PartitionCheckpointState>> acquirePartitionStates(
      final Function<CheckpointStateResponse, Set<PartitionCheckpointState>> stateFilter) {
    final ActorFuture<Set<PartitionCheckpointState>> future = createFuture();
    backupRequestHandler
        .getCheckpointState()
        .thenApplyAsync(stateFilter, this)
        .whenCompleteAsync(future, this);
    return future;
  }

  /**
   * Determines the next backup checkpoint execution time.
   *
   * <p>If no partition has a checkpoint yet, the previous schedule execution relative to "now" is
   * returned so a backup is triggered immediately on the first cycle.
   *
   * @param partitionStates the backup states of all partitions
   * @return the next backup execution instant, based on the earliest partition timestamp
   * @throws PartitionLaggingException if the partition timestamp spread exceeds the allowed
   *     threshold
   */
  private Instant determineNextBackup(final Set<PartitionCheckpointState> partitionStates)
      throws PartitionLaggingException {
    final var statistics =
        partitionStates.stream()
            .mapToLong(PartitionCheckpointState::checkpointTimestamp)
            .summaryStatistics();

    final var now = ActorClock.currentInstant();

    if (statistics.getCount() == 0) {
      // No checkpoints taken yet, return previous execution to trigger one immediately
      return previousExecution(backupSchedule, now);
    }

    final long spreadMs = statistics.getMax() - statistics.getMin();
    final var next = nextExecution(backupSchedule, now);
    final var following = nextExecution(backupSchedule, next);
    final long intervalMs = Duration.between(next, following).toMillis();

    if (spreadMs > intervalMs * LAGGING_PARTITION_FACTOR) {
      LOG.warn(
          "Detected lagging partition states: spread {} ms exceeds interval {} ms. Checkpoint scheduling will be delayed until states are more aligned.",
          spreadMs,
          intervalMs);
      throw new PartitionLaggingException(spreadMs, intervalMs);
    } else {
      return nextExecution(backupSchedule, Instant.ofEpochMilli(statistics.getMin()));
    }
  }

  /**
   * Determines the next marker checkpoint execution time.
   *
   * <p>If no partition has a checkpoint yet, the previous schedule execution relative to "now" is
   * returned so a marker is triggered immediately on the first cycle.
   *
   * @param states the checkpoint states of all partitions
   * @return the next marker execution instant
   */
  private Instant determineNextCheckpoint(final Set<PartitionCheckpointState> states) {
    final var maxCheckpoint =
        states.stream().mapToLong(PartitionCheckpointState::checkpointTimestamp).max();
    if (maxCheckpoint.isEmpty()) {
      // No checkpoints taken yet, return previous execution to trigger one immediately
      return previousExecution(checkpointSchedule, ActorClock.currentInstant());
    }
    return nextExecution(checkpointSchedule, Instant.ofEpochMilli(maxCheckpoint.getAsLong()));
  }

  private CompletableActorFuture<ExecutionResult> checkpointIfNeeded(
      final Instant nextExecution, final CheckpointType type) {
    final var now = ActorClock.currentInstant();
    final CompletableActorFuture<ExecutionResult> future = new CompletableActorFuture<>();
    if (nextExecution.isBefore(now) || nextExecution.equals(now)) {
      backupRequestHandler
          .checkpoint(type)
          .toCompletableFuture()
          .thenApplyAsync(
              id -> {
                LOG.debug("Checkpoint {} triggered with id {}", type, id);
                return new ExecutionResult(id, type, ActorClock.currentInstant(), true);
              },
              this)
          .whenCompleteAsync(future, this);
    } else {
      future.complete(new ExecutionResult(0L, type, nextExecution, false));
    }
    return future;
  }

  private void handleExecutionCompletion(final ExecutionResult result, final Throwable error) {
    final CheckpointType type = result.type;
    final long delay;
    if (error != null) {
      delay = backOff(error, type);
    } else {
      if (type == CheckpointType.MARKER) {
        markerErrorDelayMs = 0;
      } else {
        backupErrorDelayMs = 0;
      }
      if (result.checkpointTaken) {
        metrics.recordLastCheckpointId(result.checkpointId, type);
        metrics.recordLastCheckpointTime(result.checkpointTime, type);
      }
      delay = computeNextDelay(result, type);
    }
    LOG.debug("Next {} checkpoint scheduled in {} ms", type, delay);
    final Runnable task =
        type == CheckpointType.MARKER ? this::markerSchedulingTask : this::backupSchedulingTask;
    schedule(Duration.ofMillis(delay), task);
  }

  private long computeNextDelay(final ExecutionResult result, final CheckpointType type) {
    final var schedule = type == CheckpointType.MARKER ? checkpointSchedule : backupSchedule;
    final Instant base = result.checkpointTime;
    if (result.checkpointTaken) {
      final Instant next = nextExecution(schedule, base);
      metrics.recordNextExecution(next, type);
      return Math.max(0, next.toEpochMilli() - ActorClock.currentTimeMillis());
    } else {
      metrics.recordNextExecution(base, type);
      return Math.max(0, base.toEpochMilli() - ActorClock.currentTimeMillis());
    }
  }

  private long backOff(final Throwable error, final CheckpointType checkpointType) {
    final long delay;
    if (checkpointType == CheckpointType.MARKER) {
      delay = markerErrorStrategy.applyAsLong(markerErrorDelayMs);
      markerErrorDelayMs = delay;
    } else {
      delay = backupErrorStrategy.applyAsLong(backupErrorDelayMs);
      backupErrorDelayMs = delay;
    }
    LOG.debug(
        "Backing off {} scheduler for {} ms due to: {}", checkpointType, delay, error.getMessage());
    return delay;
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

  private CompletableActorFuture<ExecutionResult> backupCheckpoint(final Instant nextExecution) {
    return checkpointIfNeeded(nextExecution, CheckpointType.SCHEDULED_BACKUP);
  }

  private CompletableActorFuture<ExecutionResult> markerCheckpoint(final Instant nextExecution) {
    return checkpointIfNeeded(nextExecution, CheckpointType.MARKER);
  }

  static class CouldNotDetermineNextExecution extends RuntimeException {
    public CouldNotDetermineNextExecution(final Schedule schedule) {
      super("Could not determine next execution time for schedule: %s".formatted(schedule));
    }
  }

  static class PartitionLaggingException extends RuntimeException {
    public PartitionLaggingException(final long spreadMs, final long intervalMs) {
      super(
          "Partition lagging in states: spread %d ms exceeds interval %d ms"
              .formatted(spreadMs, intervalMs));
    }
  }

  record ExecutionResult(
      long checkpointId, CheckpointType type, Instant checkpointTime, boolean checkpointTaken) {}
}
