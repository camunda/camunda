/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.ExponentialBackoff;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Triggers marker checkpoints and scheduled backups for a single physical tenant, on that tenant's
 * own schedules. All requests are targeted at the tenant's partitions only, so tenants with
 * different backup configurations never interfere with each other.
 *
 * <p>If the log archive is enabled, it also activates the log archive once every broker is upgraded
 * (spec section 18.1), in a separate loop that ends once every partition has started its timeline.
 * The backup loop then hands over from continuous backup to the log archive, as decided by {@link
 * HandoverDecision} on each tick (spec section 18.4).
 */
public class CheckpointScheduler extends Actor implements AutoCloseable {
  private static final long BACKOFF_INITIAL_DELAY_MS = 1000;
  private static final long BACKOFF_MAX_DELAY_MS = 10_000;
  private static final long ACTIVATION_INITIAL_DELAY_MS = 1000;
  private static final long ACTIVATION_MAX_DELAY_MS = 10_000;

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointScheduler.class);
  private final String physicalTenantId;
  private final Schedule checkpointSchedule;
  private final Schedule backupSchedule;
  private final BackupRequestHandler backupRequestHandler;
  private final BrokerTopologyManager topologyManager;
  private final SchedulerMetrics metrics;

  private long markerErrorDelayMs;
  private final ExponentialBackoff markerErrorStrategy;
  private long backupErrorDelayMs;
  private final ExponentialBackoff backupErrorStrategy;
  private final boolean activateLogArchive;
  private long activationDelayMs;
  private final ExponentialBackoff activationStrategy;

  public CheckpointScheduler(
      final String physicalTenantId,
      final Schedule checkpointSchedule,
      final Schedule backupSchedule,
      final BackupRequestHandler backupRequestHandler,
      final BrokerTopologyManager topologyManager,
      final MeterRegistry meterRegistry) {
    this(
        physicalTenantId,
        checkpointSchedule,
        backupSchedule,
        backupRequestHandler,
        topologyManager,
        meterRegistry,
        false);
  }

  /**
   * @param checkpointSchedule schedule of marker checkpoints, or {@code null} for none
   * @param backupSchedule schedule of backups, or {@code null} for none
   * @param activateLogArchive whether to run the log archive activation loop
   */
  public CheckpointScheduler(
      final String physicalTenantId,
      final Schedule checkpointSchedule,
      final Schedule backupSchedule,
      final BackupRequestHandler backupRequestHandler,
      final BrokerTopologyManager topologyManager,
      final MeterRegistry meterRegistry,
      final boolean activateLogArchive) {
    super("CheckpointScheduler", null, Map.of(ACTOR_PROP_PHYSICAL_TENANT, physicalTenantId));
    this.physicalTenantId = physicalTenantId;
    this.checkpointSchedule = checkpointSchedule;
    this.backupSchedule = backupSchedule;
    metrics = new SchedulerMetrics(meterRegistry);
    this.backupRequestHandler = backupRequestHandler;
    this.topologyManager = topologyManager;
    markerErrorStrategy =
        new ExponentialBackoff(BACKOFF_MAX_DELAY_MS, BACKOFF_INITIAL_DELAY_MS, 1.2, 0);
    backupErrorStrategy =
        new ExponentialBackoff(BACKOFF_MAX_DELAY_MS, BACKOFF_INITIAL_DELAY_MS, 1.2, 0);
    this.activateLogArchive = activateLogArchive;
    activationStrategy =
        new ExponentialBackoff(ACTIVATION_MAX_DELAY_MS, ACTIVATION_INITIAL_DELAY_MS, 1.2, 0);
  }

  @Override
  protected void onActorStarted() {
    LOG.debug("Checkpoint scheduler started");
    metrics.register();
    if (checkpointSchedule != null) {
      LOG.info("Checkpoint scheduler initialized with interval {}", checkpointSchedule);
      actor.run(this::markerSchedulingTask);
    }
    if (backupSchedule != null) {
      LOG.info("Backup scheduler initialized with interval {}", backupSchedule);
      actor.run(this::backupSchedulingTask);
    }
    if (activateLogArchive) {
      LOG.info("Log archive activation started for physical tenant {}", physicalTenantId);
      actor.run(this::logArchiveActivationTask);
    }
  }

  @Override
  protected void onActorClosed() {
    LOG.debug("Checkpoint scheduler stopped");
    metrics.close();
  }

  private void markerSchedulingTask() {
    if (rescheduledRecoveringTenant(CheckpointType.MARKER)) {
      return;
    }
    acquirePartitionStates(CheckpointStateResponse::getCheckpointStates)
        .thenApply(this::determineNextCheckpoint, this)
        .andThen(this::markerCheckpoint, this)
        .onComplete((res, err) -> handleExecutionCompletion(res, err, CheckpointType.MARKER), this);
  }

  private void backupSchedulingTask() {
    if (rescheduledRecoveringTenant(CheckpointType.SCHEDULED_BACKUP)) {
      return;
    }
    acquirePartitionStates(Function.identity())
        .andThen(this::backupOrCompleteHandover, this)
        .onComplete(
            (res, err) -> handleExecutionCompletion(res, err, CheckpointType.SCHEDULED_BACKUP),
            this);
  }

  /**
   * Takes the backup if it is due, as a HANDOVER backup if the log archive is ready to take over,
   * or completes the handover instead (spec section 18.4, see {@link HandoverDecision}).
   */
  private ActorFuture<ExecutionResult> backupOrCompleteHandover(
      final CheckpointStateResponse state) {
    final var decision = HandoverDecision.decide(state, activateLogArchive);
    if (decision.action() == HandoverDecision.Action.COMPLETE_HANDOVER) {
      return completeHandover(decision.checkpointId());
    }
    return checkpointIfNeeded(determineNextBackup(state.getBackupStates()), decision.backupType());
  }

  /**
   * Sends COMPLETE_HANDOVER to every partition. The partitions only acknowledge the write, so
   * whether the handover is recorded shows in a later query: the tick is repeated with backoff
   * until it is recorded on every partition.
   */
  private ActorFuture<ExecutionResult> completeHandover(final long checkpointId) {
    LOG.info(
        "Completing the handover to the log archive at checkpoint {} for physical tenant {}",
        checkpointId,
        physicalTenantId);
    final CompletableActorFuture<ExecutionResult> future = new CompletableActorFuture<>();
    backupRequestHandler
        .completeHandover(physicalTenantId, checkpointId)
        .toCompletableFuture()
        .thenApplyAsync(ignored -> ExecutionResult.handoverPending(checkpointId), this)
        .whenCompleteAsync(future, this);
    return future;
  }

  /**
   * Activates the log archive of every partition once every broker runs at least the current minor
   * version (spec section 18.1), and stops once every partition reports a started timeline. Until
   * then it checks again with backoff; a request that reaches only some partitions, or a partition
   * that has not started its timeline yet, simply leads to another round, as activation is
   * idempotent.
   *
   * <p>Nothing is remembered between rounds, so after a restart or a move of the scheduler to
   * another broker the loop starts over, and ends after one round if every timeline is started.
   */
  private void logArchiveActivationTask() {
    if (topologyManager.isRecovering(physicalTenantId)) {
      retryLogArchiveActivation("physical tenant is in recovery mode");
      return;
    }
    if (!BrokerVersionGate.allBrokersUpgraded(
        topologyManager.getTopology(physicalTenantId), VersionUtil.getVersion())) {
      retryLogArchiveActivation("not every broker runs the current version yet");
      return;
    }
    backupRequestHandler
        .activateLogArchive(physicalTenantId)
        .whenCompleteAsync(
            (timelineStartedByPartition, error) -> {
              if (error != null) {
                retryLogArchiveActivation("activation failed: " + error.getMessage());
              } else if (timelineStartedByPartition.containsValue(false)) {
                retryLogArchiveActivation(
                    "timelines not started on every partition yet: " + timelineStartedByPartition);
              } else {
                LOG.info(
                    "Log archive activated on every partition of physical tenant {}",
                    physicalTenantId);
              }
            },
            this);
  }

  private void retryLogArchiveActivation(final String reason) {
    activationDelayMs = activationStrategy.applyAsLong(activationDelayMs);
    LOG.debug(
        "Log archive activation of physical tenant {} retried in {} ms: {}",
        physicalTenantId,
        activationDelayMs,
        reason);
    schedule(Duration.ofMillis(activationDelayMs), this::logArchiveActivationTask);
  }

  /**
   * Skips this tick's requests while the physical tenant is recovering, rescheduling the loop on
   * its normal schedule instead.
   *
   * <p>A recovering tenant is having its partitions restored, so a checkpoint taken now would
   * capture a half-restored state; a tenant whose mode this broker cannot see yet is treated the
   * same way. The loop keeps ticking rather than stopping, so it picks the schedule back up on its
   * own as soon as the tenant returns to processing mode — no restart of the actor is needed.
   *
   * @return {@code true} if the tick was skipped, in which case the loop is already rescheduled
   */
  private boolean rescheduledRecoveringTenant(final CheckpointType type) {
    if (!topologyManager.isRecovering(physicalTenantId)) {
      return false;
    }

    final var tickSchedule = type == CheckpointType.MARKER ? checkpointSchedule : backupSchedule;
    final var next = nextExecution(tickSchedule, ActorClock.currentInstant());
    metrics.recordNextExecution(next, type);
    LOG.debug(
        "Skipping {} checkpoint, physical tenant {} is in recovery mode. Next attempt at {}",
        type,
        physicalTenantId,
        next);

    final Runnable task =
        type == CheckpointType.MARKER ? this::markerSchedulingTask : this::backupSchedulingTask;
    final long delay = Math.max(0, next.toEpochMilli() - ActorClock.currentTimeMillis());
    schedule(Duration.ofMillis(delay), task);
    return true;
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
   * @return a future that completes with the projected partition states
   */
  private <T> ActorFuture<T> acquirePartitionStates(
      final Function<CheckpointStateResponse, T> stateFilter) {
    final ActorFuture<T> future = createFuture();
    backupRequestHandler
        .getCheckpointState(physicalTenantId)
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
   * @param states the backup states of all partitions
   * @return the next backup execution instant, based on the earliest partition timestamp
   */
  private Instant determineNextBackup(final Set<PartitionCheckpointState> states) {
    final var maxCheckpoint =
        states.stream().mapToLong(PartitionCheckpointState::checkpointTimestamp).max();
    if (maxCheckpoint.isEmpty()) {
      // No checkpoints taken yet, return previous execution to trigger one immediately
      return previousExecution(backupSchedule, ActorClock.currentInstant());
    }
    return nextExecution(backupSchedule, Instant.ofEpochMilli(maxCheckpoint.getAsLong()));
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
          .checkpoint(physicalTenantId, type)
          .toCompletableFuture()
          .thenApplyAsync(
              id -> {
                LOG.debug("Checkpoint {} triggered with id {}", type, id);
                return new ExecutionResult(id, type, ActorClock.currentInstant(), true, false);
              },
              this)
          .whenCompleteAsync(future, this);
    } else {
      future.complete(new ExecutionResult(0L, type, nextExecution, false, false));
    }
    return future;
  }

  private void handleExecutionCompletion(
      final ExecutionResult result, final Throwable error, final CheckpointType type) {
    final long delay;
    if (error != null) {
      delay = backOff(String.valueOf(error.getMessage()), type);
    } else if (result.handoverPending) {
      delay = backOff("handover at checkpoint %d pending".formatted(result.checkpointId), type);
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

  private long backOff(final String reason, final CheckpointType checkpointType) {
    final long delay;
    if (checkpointType == CheckpointType.MARKER) {
      delay = markerErrorStrategy.applyAsLong(markerErrorDelayMs);
      markerErrorDelayMs = delay;
    } else {
      delay = backupErrorStrategy.applyAsLong(backupErrorDelayMs);
      backupErrorDelayMs = delay;
    }
    LOG.debug("Backing off {} scheduler for {} ms due to: {}", checkpointType, delay, reason);
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

  private CompletableActorFuture<ExecutionResult> markerCheckpoint(final Instant nextExecution) {
    return checkpointIfNeeded(nextExecution, CheckpointType.MARKER);
  }

  static class CouldNotDetermineNextExecution extends RuntimeException {
    public CouldNotDetermineNextExecution(final Schedule schedule) {
      super("Could not determine next execution time for schedule: %s".formatted(schedule));
    }
  }

  /**
   * @param handoverPending whether COMPLETE_HANDOVER was sent instead of a checkpoint, so the tick
   *     is repeated with backoff until the handover is recorded everywhere
   */
  record ExecutionResult(
      long checkpointId,
      CheckpointType type,
      Instant checkpointTime,
      boolean checkpointTaken,
      boolean handoverPending) {

    static ExecutionResult handoverPending(final long checkpointId) {
      return new ExecutionResult(
          checkpointId, CheckpointType.HANDOVER, ActorClock.currentInstant(), false, true);
    }
  }
}
