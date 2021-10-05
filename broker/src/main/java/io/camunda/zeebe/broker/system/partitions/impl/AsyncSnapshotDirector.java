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
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.snapshots.SnapshotException;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.SchedulingHints;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
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
  private final Set<FailureListener> listeners = new HashSet<>();
  private final BooleanSupplier isLastWrittenPositionCommitted;

  private Long lastWrittenEventPosition;
  private TransientSnapshot pendingSnapshot;
  private long lowerBoundSnapshotPosition;
  private boolean takingSnapshot;
  private boolean persistingSnapshot;
  private volatile HealthStatus healthStatus = HealthStatus.HEALTHY;
  private long commitPosition;
  private final int partitionId;

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
    if (streamProcessorMode == StreamProcessorMode.REPLAY) {
      isLastWrittenPositionCommitted = () -> true;
    } else {
      isLastWrittenPositionCommitted = () -> lastWrittenEventPosition <= commitPosition;
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
    actor.setSchedulingHints(SchedulingHints.ioBound());
    final var firstSnapshotTime =
        RandomDuration.getRandomDurationMinuteBased(MINIMUM_SNAPSHOT_PERIOD, snapshotRate);
    actor.runDelayed(firstSnapshotTime, this::scheduleSnapshotOnRate);

    lastWrittenEventPosition = null;
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(null);
    }

    return super.closeAsync();
  }

  @Override
  protected void handleFailure(final Exception failure) {
    LOG.error(
        "No snapshot was taken due to failure in '{}'. Will try to take snapshot after snapshot period {}. {}",
        actorName,
        snapshotRate,
        failure);

    resetStateOnFailure();
    healthStatus = HealthStatus.UNHEALTHY;

    for (final var listener : listeners) {
      listener.onFailure();
    }
  }

  /**
   * Create an AsyncSnapshotDirector that can take snapshot when the Streamprocessor is in
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
    actor.runAtFixedRate(snapshotRate, this::prepareTakingSnapshot);
    prepareTakingSnapshot();
  }

  public void forceSnapshot() {
    actor.call(this::prepareTakingSnapshot);
  }

  @Override
  public HealthStatus getHealthStatus() {
    return healthStatus;
  }

  @Override
  public void addFailureListener(final FailureListener listener) {
    actor.run(() -> listeners.add(listener));
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> listeners.remove(failureListener));
  }

  private void prepareTakingSnapshot() {
    if (takingSnapshot) {
      return;
    }

    takingSnapshot = true;
    final var futureLastProcessedPosition = streamProcessor.getLastProcessedPositionAsync();
    actor.runOnCompletion(
        futureLastProcessedPosition,
        (lastProcessedPosition, error) -> {
          if (error == null) {
            if (lastProcessedPosition == StreamProcessor.UNSET_POSITION) {
              LOG.debug(
                  "We will skip taking this snapshot, because we haven't processed something yet.");
              takingSnapshot = false;
              return;
            }

            lowerBoundSnapshotPosition = lastProcessedPosition;
            takeSnapshot();

          } else {
            LOG.error(ERROR_MSG_ON_RESOLVE_PROCESSED_POS, error);
            takingSnapshot = false;
          }
        });
  }

  private void takeSnapshot() {
    final var transientSnapshotFuture =
        stateController.takeTransientSnapshot(lowerBoundSnapshotPosition);
    transientSnapshotFuture.onComplete(
        (transientSnapshot, snapshotTakenError) -> {
          if (snapshotTakenError != null) {
            if (snapshotTakenError instanceof SnapshotException.SnapshotAlreadyExistsException) {
              LOG.debug("Did not take a snapshot. {}", snapshotTakenError.getMessage());
            } else {
              LOG.error("Failed to take a snapshot for {}", processorName, snapshotTakenError);
            }
            resetStateOnFailure();
            return;
          }

          onTransientSnapshotTaken(transientSnapshot);
        });
  }

  private void onTransientSnapshotTaken(final TransientSnapshot transientSnapshot) {

    pendingSnapshot = transientSnapshot;
    onRecovered();

    final ActorFuture<Long> lastWrittenPosition = streamProcessor.getLastWrittenPositionAsync();
    actor.runOnCompletion(lastWrittenPosition, this::onLastWrittenPositionReceived);
  }

  private void onLastWrittenPositionReceived(final Long endPosition, final Throwable error) {
    if (error == null) {
      LOG.info(LOG_MSG_WAIT_UNTIL_COMMITTED, endPosition, commitPosition);
      lastWrittenEventPosition = endPosition;
      persistingSnapshot = false;
      persistSnapshotIfLastWrittenPositionCommitted();
    } else {
      resetStateOnFailure();
      LOG.error(ERROR_MSG_ON_RESOLVE_WRITTEN_POS, error);
    }
  }

  private void onRecovered() {
    if (healthStatus != HealthStatus.HEALTHY) {
      healthStatus = HealthStatus.HEALTHY;
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
    if (pendingSnapshot != null
        && lastWrittenEventPosition != null
        && isLastWrittenPositionCommitted.getAsBoolean()
        && !persistingSnapshot) {
      persistingSnapshot = true;

      LOG.debug(
          "Current commit position {} >= {}, committing snapshot {}.",
          commitPosition,
          lastWrittenEventPosition,
          pendingSnapshot);
      final var snapshotPersistFuture = pendingSnapshot.persist();

      snapshotPersistFuture.onComplete(
          (snapshot, persistError) -> {
            if (persistError != null) {
              if (persistError instanceof SnapshotNotFoundException) {
                LOG.warn(
                    "Failed to persist transient snapshot {}. Nothing to worry if a newer snapshot exists.",
                    pendingSnapshot,
                    persistError);
              } else {
                LOG.error(ERROR_MSG_MOVE_SNAPSHOT, persistError);
              }
            }
            lastWrittenEventPosition = null;
            takingSnapshot = false;
            pendingSnapshot = null;
            persistingSnapshot = false;
          });
    }
  }

  private void resetStateOnFailure() {
    lastWrittenEventPosition = null;
    takingSnapshot = false;
    if (pendingSnapshot != null) {
      pendingSnapshot.abort();
      pendingSnapshot = null;
    }
  }
}
