/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.atomix.raft.RaftCommittedEntryListener;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.camunda.zeebe.broker.system.partitions.NoEntryAtSnapshotPosition;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.streamprocessor.StreamProcessor;
import io.camunda.zeebe.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

public final class AsyncSnapshotDirector extends Actor
    implements RaftCommittedEntryListener, HealthMonitorable {

  public static final Duration MINIMUM_SNAPSHOT_PERIOD = Duration.ofMinutes(1);

  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;
  private static final String LOG_MSG_WAIT_UNTIL_COMMITTED =
      "Finished taking temporary snapshot, need to wait until last written event position {} is committed, current commit position is {}. After that snapshot will be committed.";
  private static final String ERROR_MSG_ON_RESOLVE_PROCESSED_POS =
      "Unexpected error in resolving last processed position.";
  private static final String ERROR_MSG_ON_RESOLVE_WRITTEN_POS =
      "Unexpected error in resolving last written position.";
  private static final String ERROR_MSG_MOVE_SNAPSHOT =
      "Unexpected exception occurred on moving valid snapshot.";

  private final StateController stateController;
  private final Duration snapshotRate;
  private final String processorName;
  private final StreamProcessor streamProcessor;
  private final String actorName;
  private final StreamProcessorMode streamProcessorMode;
  private final Set<FailureListener> listeners = new HashSet<>();
  private final BooleanSupplier isLastWrittenPositionCommitted;

  @SuppressWarnings("java:S3077") // allow volatile here, health is immutable
  private volatile HealthReport healthReport = HealthReport.healthy(this);

  private long commitPosition;
  private final int partitionId;

  private final SnapshotInProgress snapshotInProgress = new SnapshotInProgress();

  private AsyncSnapshotDirector(
      final int nodeId,
      final int partitionId,
      final StreamProcessor streamProcessor,
      final StateController stateController,
      final Duration snapshotRate,
      final StreamProcessorMode streamProcessorMode) {
    this.streamProcessor = streamProcessor;
    this.stateController = stateController;
    processorName = streamProcessor.getName();
    this.snapshotRate = snapshotRate;
    this.partitionId = partitionId;
    actorName = buildActorName(nodeId, "SnapshotDirector", this.partitionId);
    this.streamProcessorMode = streamProcessorMode;
    if (streamProcessorMode == StreamProcessorMode.REPLAY) {
      isLastWrittenPositionCommitted = () -> true;
    } else {
      isLastWrittenPositionCommitted =
          () -> snapshotInProgress.lastWrittenPosition <= commitPosition;
    }
  }

  @Override
  protected Map<String, String> createContext() {
    final var context = super.createContext();
    context.put(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId));
    return context;
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarting() {
    final var firstSnapshotTime =
        RandomDuration.getRandomDurationMinuteBased(MINIMUM_SNAPSHOT_PERIOD, snapshotRate);
    actor.runDelayed(firstSnapshotTime, this::scheduleSnapshotOnRate);

    snapshotInProgress.lastWrittenPosition = null;
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(null);
    }

    return super.closeAsync();
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    LOG.error(
        "No snapshot was taken due to failure in '{}'. Will try to take snapshot after snapshot period {}. {}",
        actorName,
        snapshotRate,
        failure);

    snapshotInProgress.fail(failure);
    healthReport = HealthReport.unhealthy(this).withIssue(failure);

    for (final var listener : listeners) {
      listener.onFailure(healthReport);
    }
  }

  /**
   * Create an AsyncSnapshotDirector that can take snapshot when the StreamProcessor is in
   * continuous replay mode.
   *
   * @param nodeId id of this broker
   * @param partitionId partition id
   * @param streamProcessor stream processor for the partition
   * @param stateController state controller that manages state
   * @param snapshotRate rate at which the snapshot is taken
   * @return snapshot director
   */
  public static AsyncSnapshotDirector ofReplayMode(
      final int nodeId,
      final int partitionId,
      final StreamProcessor streamProcessor,
      final StateController stateController,
      final Duration snapshotRate) {
    return new AsyncSnapshotDirector(
        nodeId,
        partitionId,
        streamProcessor,
        stateController,
        snapshotRate,
        StreamProcessorMode.REPLAY);
  }

  /**
   * Create an AsyncSnapshotDirector that can take snapshot when the StreamProcessor is in
   * processing mode
   *
   * @param nodeId id of this broker
   * @param partitionId partition id
   * @param streamProcessor stream processor for the partition
   * @param stateController state controller that manages state
   * @param snapshotRate rate at which the snapshot is taken
   * @return snapshot director
   */
  public static AsyncSnapshotDirector ofProcessingMode(
      final int nodeId,
      final int partitionId,
      final StreamProcessor streamProcessor,
      final StateController stateController,
      final Duration snapshotRate) {
    return new AsyncSnapshotDirector(
        nodeId,
        partitionId,
        streamProcessor,
        stateController,
        snapshotRate,
        StreamProcessorMode.PROCESSING);
  }

  private void scheduleSnapshotOnRate() {
    actor.runAtFixedRate(snapshotRate, () -> prepareTakingSnapshot(new CompletableActorFuture<>()));
    prepareTakingSnapshot(new CompletableActorFuture<>());
  }

  /**
   * Directly take a snapshot, independently of the scheduled snapshots.
   *
   * @return A future that is completed successfully when the snapshot was taken. If the snapshot
   *     was skipped, the future is also completed successfully but with a null.
   */
  public CompletableActorFuture<PersistedSnapshot> forceSnapshot() {
    final var newSnapshotFuture = new CompletableActorFuture<PersistedSnapshot>();
    actor.call(() -> prepareTakingSnapshot(newSnapshotFuture));
    return newSnapshotFuture;
  }

  @Override
  public HealthReport getHealthReport() {
    return healthReport;
  }

  @Override
  public void addFailureListener(final FailureListener listener) {
    actor.run(() -> listeners.add(listener));
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> listeners.remove(failureListener));
  }

  private void prepareTakingSnapshot(
      final CompletableActorFuture<PersistedSnapshot> newSnapshotFuture) {
    if (snapshotInProgress.snapshotFuture != null) {
      LOG.debug("Already taking snapshot, skipping this request for a new snapshot");
      newSnapshotFuture.complete(null);
      return;
    }

    snapshotInProgress.snapshotFuture = newSnapshotFuture;
    final var futureLastProcessedPosition = streamProcessor.getLastProcessedPositionAsync();
    actor.runOnCompletion(
        futureLastProcessedPosition,
        (lastProcessedPosition, error) -> {
          if (error == null) {
            if (lastProcessedPosition == StreamProcessor.UNSET_POSITION) {
              LOG.debug(
                  "We will skip taking this snapshot, because we haven't processed something yet.");
              snapshotInProgress.complete(null);
              return;
            }

            snapshotInProgress.lowerBoundSnapshotPosition = lastProcessedPosition;
            takeSnapshot();

          } else {
            LOG.error(ERROR_MSG_ON_RESOLVE_PROCESSED_POS, error);
            snapshotInProgress.fail(error);
          }
        });
  }

  private void takeSnapshot() {
    final var transientSnapshotFuture =
        stateController.takeTransientSnapshot(snapshotInProgress.lowerBoundSnapshotPosition);
    transientSnapshotFuture.onComplete(
        (transientSnapshot, snapshotTakenError) -> {
          if (snapshotTakenError != null) {
            logSnapshotTakenError(snapshotTakenError);
            snapshotInProgress.fail(snapshotTakenError);
            return;
          }

          onTransientSnapshotTaken(transientSnapshot);
        });
  }

  private void logSnapshotTakenError(final Throwable snapshotTakenError) {
    if (snapshotTakenError instanceof SnapshotException.SnapshotAlreadyExistsException) {
      LOG.debug("Did not take a snapshot. {}", snapshotTakenError.getMessage());
    } else if (snapshotTakenError instanceof NoEntryAtSnapshotPosition
        && streamProcessorMode == StreamProcessorMode.REPLAY) {
      LOG.debug(
          "Did not take a snapshot: {}. Most likely this partition has not received the entry yet. Will retry in {}",
          snapshotTakenError.getMessage(),
          snapshotRate);
    } else {
      LOG.error("Failed to take a snapshot for {}", processorName, snapshotTakenError);
    }
  }

  private void onTransientSnapshotTaken(final TransientSnapshot transientSnapshot) {

    snapshotInProgress.pendingSnapshot = transientSnapshot;
    onRecovered();

    final ActorFuture<Long> lastWrittenPosition = streamProcessor.getLastWrittenPositionAsync();
    actor.runOnCompletion(lastWrittenPosition, this::onLastWrittenPositionReceived);
  }

  private void onLastWrittenPositionReceived(final Long endPosition, final Throwable error) {
    if (error == null) {
      LOG.info(LOG_MSG_WAIT_UNTIL_COMMITTED, endPosition, commitPosition);
      snapshotInProgress.lastWrittenPosition = endPosition;
      persistSnapshotIfLastWrittenPositionCommitted();
    } else {
      snapshotInProgress.fail(error);
      LOG.error(ERROR_MSG_ON_RESOLVE_WRITTEN_POS, error);
    }
  }

  private void onRecovered() {
    if (!healthReport.isHealthy()) {
      healthReport = HealthReport.healthy(this);
      listeners.forEach(FailureListener::onRecovered);
    }
  }

  @Override
  public void onCommit(final IndexedRaftLogEntry indexedRaftLogEntry) {
    // is called by the Leader Role and gives the last committed entry, where we
    // can extract the highest position, which corresponds to the last committed position
    if (indexedRaftLogEntry.isApplicationEntry()) {
      final var committedPosition = indexedRaftLogEntry.getApplicationEntry().highestPosition();
      newPositionCommitted(committedPosition);
    }
  }

  public void newPositionCommitted(final long currentCommitPosition) {
    actor.run(
        () -> {
          commitPosition = currentCommitPosition;
          persistSnapshotIfLastWrittenPositionCommitted();
        });
  }

  private void persistSnapshotIfLastWrittenPositionCommitted() {
    if (snapshotInProgress.isWaitingForLastWrittenPositionToCommit()
        && isLastWrittenPositionCommitted.getAsBoolean()) {
      snapshotInProgress.persistingSnapshot = true;

      LOG.debug(
          "Current commit position {} >= {}, committing snapshot {}.",
          commitPosition,
          snapshotInProgress.lastWrittenPosition,
          snapshotInProgress.pendingSnapshot);
      final var snapshotPersistFuture = snapshotInProgress.pendingSnapshot.persist();

      snapshotPersistFuture.onComplete(
          (snapshot, persistError) -> {
            if (persistError != null) {
              snapshotInProgress.snapshotFuture.completeExceptionally(persistError);
              if (persistError instanceof SnapshotNotFoundException) {
                LOG.warn(
                    "Failed to persist transient snapshot {}. Nothing to worry if a newer snapshot exists.",
                    snapshotInProgress.pendingSnapshot,
                    persistError);
              } else {
                LOG.error(ERROR_MSG_MOVE_SNAPSHOT, persistError);
              }
              snapshotInProgress.fail(persistError);
            } else {
              snapshotInProgress.complete(snapshot);
            }
          });
    }
  }

  /** Keep track of the state of an ongoing snapshotting */
  static final class SnapshotInProgress {
    private Long lastWrittenPosition;
    private TransientSnapshot pendingSnapshot;
    private long lowerBoundSnapshotPosition;
    private CompletableActorFuture<PersistedSnapshot> snapshotFuture;
    private boolean persistingSnapshot;

    private SnapshotInProgress() {
      reset();
    }

    void complete(final PersistedSnapshot snapshot) {
      snapshotFuture.complete(snapshot);
      reset();
    }

    void fail(final Throwable error) {
      if (snapshotFuture != null && !snapshotFuture.isDone()) {
        snapshotFuture.completeExceptionally(error);
      }
      if (pendingSnapshot != null) {
        pendingSnapshot.abort();
      }
      reset();
    }

    boolean isWaitingForLastWrittenPositionToCommit() {
      return pendingSnapshot != null && lastWrittenPosition != null && persistingSnapshot;
    }

    void reset() {
      lastWrittenPosition = null;
      pendingSnapshot = null;
      lowerBoundSnapshotPosition = -1;
      snapshotFuture = null;
      persistingSnapshot = false;
    }
  }
}
