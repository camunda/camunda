/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.atomix.raft.RaftApplicationEntryCommittedPositionListener;
import io.camunda.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.camunda.zeebe.broker.system.partitions.NoEntryAtSnapshotPosition;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotAlreadyExistsException;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferService;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public final class AsyncSnapshotDirector extends Actor
    implements RaftApplicationEntryCommittedPositionListener,
        HealthMonitorable,
        SnapshotTransferService.TakeSnapshot {

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
  private final Callable<CompletableFuture<Void>> flushLog;
  private final StatePositionSupplier positionSupplier;
  private final Set<FailureListener> listeners = new HashSet<>();
  private final int partitionId;
  private final TreeMap<Long, ActorFuture<Void>> commitAwaiters = new TreeMap<>();
  private CompletableActorFuture<PersistedSnapshot> ongoingSnapshotFuture;

  @SuppressWarnings("java:S3077") // allow volatile here, health is immutable
  private volatile HealthReport healthReport;

  private long commitPosition;

  private AsyncSnapshotDirector(
      final int partitionId,
      final StreamProcessor streamProcessor,
      final StateController stateController,
      final Duration snapshotRate,
      final StreamProcessorMode streamProcessorMode,
      final Callable<CompletableFuture<Void>> flushLog,
      final StatePositionSupplier positionSupplier) {
    this.streamProcessor = streamProcessor;
    this.stateController = stateController;
    processorName = streamProcessor.getName();
    this.snapshotRate = snapshotRate;
    this.partitionId = partitionId;
    actorName = actorName(partitionId);
    this.streamProcessorMode = streamProcessorMode;
    this.flushLog = flushLog;
    this.positionSupplier = positionSupplier;
    healthReport = HealthReport.healthy(this);
  }

  /**
   * Create an AsyncSnapshotDirector that can take snapshot when the StreamProcessor is in the
   * provided replay mode
   *
   * @param partitionId partition id
   * @param streamProcessor stream processor for the partition
   * @param stateController state controller that manages state
   * @param streamProcessorMode mode of the stream processor
   * @param snapshotRate rate at which the snapshot is taken
   * @param flushLog callable to flush the log
   * @param positionSupplier supplier for positions from the state
   * @return snapshot director
   */
  public static AsyncSnapshotDirector of(
      final int partitionId,
      final StreamProcessor streamProcessor,
      final StateController stateController,
      final StreamProcessorMode streamProcessorMode,
      final Duration snapshotRate,
      final Callable<CompletableFuture<Void>> flushLog,
      final StatePositionSupplier positionSupplier) {
    return new AsyncSnapshotDirector(
        partitionId,
        streamProcessor,
        stateController,
        snapshotRate,
        streamProcessorMode,
        flushLog,
        positionSupplier);
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
    actor.schedule(firstSnapshotTime, this::scheduleSnapshotOnRate);
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
        "No snapshot was taken due to failure in '{}'. Will try to take snapshot after snapshot period {}.",
        actorName,
        snapshotRate,
        failure);

    resetStateOnFailure(failure);
    healthReport = HealthReport.unhealthy(this).withIssue(failure, Instant.now());
    notifyAllListeners();
  }

  public static String actorName(final int partitionId) {
    return buildActorName("SnapshotDirector", partitionId);
  }

  private void notifyAllListeners() {
    for (final var listener : listeners) {
      listener.onFailure(healthReport);
    }
  }

  private void scheduleSnapshotOnRate() {
    actor.runAtFixedRate(snapshotRate, () -> trySnapshot(false));
    trySnapshot(false);
  }

  /**
   * Directly take a snapshot, independently of the scheduled snapshots.
   *
   * @return A future that is completed successfully when the snapshot was taken. If the snapshot
   *     was skipped, the future is also completed successfully but with a null.
   */
  public ActorFuture<PersistedSnapshot> forceSnapshot() {
    final var newSnapshotFuture = new CompletableActorFuture<PersistedSnapshot>();
    actor.call(() -> trySnapshot(true).onComplete(newSnapshotFuture));
    return newSnapshotFuture;
  }

  @Override
  public String componentName() {
    return actorName;
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

  // Try taking a snapshot. Snapshot may be skipped if there is already an ongoing snapshot or if
  // there is nothing to snapshot. Future is completed with null if the snapshot is skipped.
  // Otherwise, future is completed with the committed snapshot, or completed exceptionally if
  // snapshotting fails.
  private ActorFuture<PersistedSnapshot> trySnapshot(final boolean forceSnapshot) {
    if (ongoingSnapshotFuture != null) {
      LOG.debug("Already taking snapshot, skipping this request for a new snapshot");
      return CompletableActorFuture.completed(null);
    }

    final CompletableActorFuture<PersistedSnapshot> snapshotFuture = new CompletableActorFuture<>();
    ongoingSnapshotFuture = snapshotFuture;
    final InProgressSnapshot inProgressSnapshot = new InProgressSnapshot();

    streamProcessor
        .getLastProcessedPositionAsync()
        .onComplete(
            (position, error) -> {
              if (error != null) {
                LOG.error(ERROR_MSG_ON_RESOLVE_PROCESSED_POS, error);
                snapshotFuture.completeExceptionally(error);
              } else {
                inProgressSnapshot.lowerBoundSnapshotPosition =
                    position == StreamProcessor.UNSET_POSITION ? 0L : position;
                if (inProgressSnapshot.lowerBoundSnapshotPosition == 0 && !forceSnapshot) {
                  LOG.debug(
                      "We will skip taking this snapshot, because we haven't processed anything yet.");
                  snapshotFuture.complete(null);
                  return;
                }
                snapshot(inProgressSnapshot, forceSnapshot).onComplete(snapshotFuture);
              }
            });

    snapshotFuture.onComplete(
        (snapshot, snapshotError) -> {
          if (snapshotError != null && inProgressSnapshot.pendingSnapshot != null) {
            inProgressSnapshot.pendingSnapshot.abort();
          }
          // We allow only one ongoing snapshot. Reset the future to indicate there is
          // no ongoing snapshot.
          ongoingSnapshotFuture = null;
        });
    return snapshotFuture;
  }

  private ActorFuture<PersistedSnapshot> snapshot(
      final InProgressSnapshot inProgressSnapshot, final boolean forceSnapshot) {
    return takeTransientSnapshot(inProgressSnapshot, forceSnapshot)
        .andThen(() -> getPositionsAfterSnapshot(inProgressSnapshot), actor)
        .andThen(() -> waitUntilLastWrittenPositionIsCommitted(inProgressSnapshot), actor)
        .andThen(this::flushJournal, actor)
        .andThen(() -> persistSnapshot(inProgressSnapshot), actor);
  }

  private ActorFuture<Void> flushJournal() {
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    try {
      flushLog
          .call()
          .whenComplete(
              (ignore, error) -> {
                if (error != null) {
                  LOG.warn("Failed to flush journal before committing snapshot", error);
                  future.completeExceptionally(error);
                } else {
                  future.complete(null);
                }
              });
    } catch (final Exception e) {
      LOG.warn("Failed to flush journal before committing snapshot", e);
      future.completeExceptionally(e);
    }
    return future;
  }

  private ActorFuture<PersistedSnapshot> persistSnapshot(
      final InProgressSnapshot inProgressSnapshot) {
    final var snapshotPersisted =
        inProgressSnapshot
            .pendingSnapshot
            .withLastFollowupEventPosition(inProgressSnapshot.lastWrittenPosition)
            .withMaxExportedPosition(inProgressSnapshot.maxExportedPosition)
            .persist();
    snapshotPersisted.onComplete(
        (snapshot, persistError) -> {
          switch (persistError) {
            case null -> {}
            case final SnapshotNotFoundException notFoundException ->
                LOG.warn(
                    "Failed to persist transient snapshot {}. Nothing to worry if a newer snapshot exists.",
                    inProgressSnapshot.pendingSnapshot,
                    notFoundException);
            case final SnapshotAlreadyExistsException alreadyExistsException ->
                LOG.debug(
                    "Failed to persist transient snapshot {}. Snapshot already exists.",
                    inProgressSnapshot.pendingSnapshot,
                    alreadyExistsException);
            default -> LOG.error(ERROR_MSG_MOVE_SNAPSHOT, persistError);
          }
        });
    return snapshotPersisted;
  }

  private ActorFuture<Void> getPositionsAfterSnapshot(final InProgressSnapshot inProgressSnapshot) {
    inProgressSnapshot.maxExportedPosition = positionSupplier.getHighestExportedPosition();
    return streamProcessor
        .getLastWrittenPositionAsync()
        .andThen(
            (position, error) -> {
              if (error != null) {
                LOG.error(ERROR_MSG_ON_RESOLVE_WRITTEN_POS, error);
                return CompletableActorFuture.completedExceptionally(error);
              } else {
                inProgressSnapshot.lastWrittenPosition = position;
                return CompletableActorFuture.completed(null);
              }
            },
            actor);
  }

  private ActorFuture<Void> waitUntilLastWrittenPositionIsCommitted(
      final InProgressSnapshot inProgressSnapshot) {
    if (streamProcessorMode == StreamProcessorMode.REPLAY
        || commitPosition >= inProgressSnapshot.lastWrittenPosition) {
      return CompletableActorFuture.completed(null);
    } else {
      LOG.info(
          LOG_MSG_WAIT_UNTIL_COMMITTED, inProgressSnapshot.lastWrittenPosition, commitPosition);
      return commitAwaiters.computeIfAbsent(
          inProgressSnapshot.lastWrittenPosition, k -> new CompletableActorFuture<>());
    }
  }

  private ActorFuture<Void> takeTransientSnapshot(
      final InProgressSnapshot inProgressSnapshot, final boolean forceSnapshot) {
    return stateController
        .takeTransientSnapshot(inProgressSnapshot.lowerBoundSnapshotPosition, forceSnapshot)
        .andThen(
            (snapshot, error) -> {
              if (error != null) {
                logSnapshotTakenError(error);
                return CompletableActorFuture.completedExceptionally(error);
              } else {
                inProgressSnapshot.pendingSnapshot = snapshot;
                onRecovered();
                return CompletableActorFuture.completed(null);
              }
            },
            actor);
  }

  void logSnapshotTakenError(final Throwable snapshotTakenError) {
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

  private void onRecovered() {
    if (!healthReport.isHealthy()) {
      healthReport = HealthReport.healthy(this);
      listeners.forEach(l -> l.onRecovered(healthReport));
    }
  }

  @Override
  public void onCommit(final long committedPosition) {
    newPositionCommitted(committedPosition);
  }

  public void newPositionCommitted(final long currentCommitPosition) {
    actor.run(
        () -> {
          commitPosition = Math.max(currentCommitPosition, commitPosition);
          final var futuresToComplete = commitAwaiters.headMap(commitPosition, true);
          futuresToComplete.forEach((k, f) -> f.complete(null));
          futuresToComplete.clear();
        });
  }

  private void resetStateOnFailure(final Throwable failure) {
    if (ongoingSnapshotFuture != null && !ongoingSnapshotFuture.isDone()) {
      ongoingSnapshotFuture.completeExceptionally(failure);
    }

    ongoingSnapshotFuture = null;
  }

  @Override
  public ActorFuture<PersistedSnapshot> takeSnapshot(final long lastProcessedPosition) {
    final var inProgressSnapshot = new InProgressSnapshot();
    inProgressSnapshot.lowerBoundSnapshotPosition = lastProcessedPosition;
    final var result = actor.<PersistedSnapshot>createFuture();
    actor.run(() -> snapshot(inProgressSnapshot, true).onComplete(result));
    return result;
  }

  @VisibleForTesting
  public ActorFuture<Long> getCommitPosition() {
    return actor.call(() -> commitPosition);
  }

  private static final class InProgressSnapshot {
    private long lastWrittenPosition;
    private TransientSnapshot pendingSnapshot;
    private long lowerBoundSnapshotPosition;
    private long maxExportedPosition;
  }
}
