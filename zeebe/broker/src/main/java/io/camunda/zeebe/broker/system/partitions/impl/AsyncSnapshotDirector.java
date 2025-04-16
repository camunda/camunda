/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.atomix.raft.RaftApplicationEntryCommittedPositionListener;
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
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
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
    implements RaftApplicationEntryCommittedPositionListener, HealthMonitorable {

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
      final Callable<CompletableFuture<Void>> flushLog) {
    this.streamProcessor = streamProcessor;
    this.stateController = stateController;
    processorName = streamProcessor.getName();
    this.snapshotRate = snapshotRate;
    this.partitionId = partitionId;
    actorName = buildActorName("SnapshotDirector", this.partitionId);
    this.streamProcessorMode = streamProcessorMode;
    this.flushLog = flushLog;
    healthReport = HealthReport.healthy(this);
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

    for (final var listener : listeners) {
      listener.onFailure(healthReport);
    }
  }

  /**
   * Create an AsyncSnapshotDirector that can take snapshot when the StreamProcessor is in
   * continuous replay mode.
   *
   * @param partitionId partition id
   * @param streamProcessor stream processor for the partition
   * @param stateController state controller that manages state
   * @param snapshotRate rate at which the snapshot is taken
   * @return snapshot director
   */
  public static AsyncSnapshotDirector ofReplayMode(
      final int partitionId,
      final StreamProcessor streamProcessor,
      final StateController stateController,
      final Duration snapshotRate,
      final Callable<CompletableFuture<Void>> flushLog) {
    return new AsyncSnapshotDirector(
        partitionId,
        streamProcessor,
        stateController,
        snapshotRate,
        StreamProcessorMode.REPLAY,
        flushLog);
  }

  /**
   * Create an AsyncSnapshotDirector that can take snapshot when the StreamProcessor is in
   * processing mode
   *
   * @param partitionId partition id
   * @param streamProcessor stream processor for the partition
   * @param stateController state controller that manages state
   * @param snapshotRate rate at which the snapshot is taken
   * @return snapshot director
   */
  public static AsyncSnapshotDirector ofProcessingMode(
      final int partitionId,
      final StreamProcessor streamProcessor,
      final StateController stateController,
      final Duration snapshotRate,
      final Callable<CompletableFuture<Void>> flushLog) {
    return new AsyncSnapshotDirector(
        partitionId,
        streamProcessor,
        stateController,
        snapshotRate,
        StreamProcessorMode.PROCESSING,
        flushLog);
  }

  private void scheduleSnapshotOnRate() {
    actor.runAtFixedRate(snapshotRate, this::trySnapshot);
    trySnapshot();
  }

  /**
   * Directly take a snapshot, independently of the scheduled snapshots.
   *
   * @return A future that is completed successfully when the snapshot was taken. If the snapshot
   *     was skipped, the future is also completed successfully but with a null.
   */
  public CompletableActorFuture<PersistedSnapshot> forceSnapshot() {
    final var newSnapshotFuture = new CompletableActorFuture<PersistedSnapshot>();
    actor.call(() -> trySnapshot().onComplete(newSnapshotFuture));
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
  private ActorFuture<PersistedSnapshot> trySnapshot() {
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
              } else if (position == StreamProcessor.UNSET_POSITION) {
                LOG.debug(
                    "We will skip taking this snapshot, because we haven't processed anything yet.");
                snapshotFuture.complete(null);
              } else {
                inProgressSnapshot.lowerBoundSnapshotPosition = position;
                snapshot(inProgressSnapshot).onComplete(snapshotFuture);
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

  private ActorFuture<PersistedSnapshot> snapshot(final InProgressSnapshot inProgressSnapshot) {
    return takeTransientSnapshot(inProgressSnapshot)
        .andThen(() -> getLastWrittenPosition(inProgressSnapshot), actor)
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
            .persist();
    snapshotPersisted.onComplete(
        (snapshot, persistError) -> {
          if (persistError != null) {
            if (persistError instanceof SnapshotNotFoundException) {
              LOG.warn(
                  "Failed to persist transient snapshot {}. Nothing to worry if a newer snapshot exists.",
                  inProgressSnapshot.pendingSnapshot,
                  persistError);
            } else {
              LOG.error(ERROR_MSG_MOVE_SNAPSHOT, persistError);
            }
          }
        });
    return snapshotPersisted;
  }

  private ActorFuture<Void> getLastWrittenPosition(final InProgressSnapshot inProgressSnapshot) {
    final ActorFuture<Void> lastWrittenPositionReceived = new CompletableActorFuture<>();
    streamProcessor
        .getLastWrittenPositionAsync()
        .onComplete(
            (position, error) -> {
              if (error != null) {
                LOG.error(ERROR_MSG_ON_RESOLVE_WRITTEN_POS, error);
                lastWrittenPositionReceived.completeExceptionally(error);
              } else {
                inProgressSnapshot.lastWrittenPosition = position;
                lastWrittenPositionReceived.complete(null);
              }
            });
    return lastWrittenPositionReceived;
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

  private ActorFuture<Void> takeTransientSnapshot(final InProgressSnapshot inProgressSnapshot) {
    final ActorFuture<Void> snapshotTaken = new CompletableActorFuture<>();
    stateController
        .takeTransientSnapshot(inProgressSnapshot.lowerBoundSnapshotPosition)
        .onComplete(
            (snapshot, error) -> {
              if (error != null) {
                logSnapshotTakenError(error);
                snapshotTaken.completeExceptionally(error);
              } else {
                inProgressSnapshot.pendingSnapshot = snapshot;
                snapshotTaken.complete(null);
                onRecovered();
              }
            });
    return snapshotTaken;
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
          commitPosition = currentCommitPosition;
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

  private static final class InProgressSnapshot {
    private long lastWrittenPosition;
    private TransientSnapshot pendingSnapshot;
    private long lowerBoundSnapshotPosition;
  }
}
