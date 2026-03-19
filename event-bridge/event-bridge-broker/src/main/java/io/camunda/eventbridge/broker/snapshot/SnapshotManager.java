/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.snapshot;

import io.camunda.eventbridge.broker.offset.OffsetStore;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotException;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor responsible for triggering RAFT snapshots that persist offset state for a single partition.
 *
 * <h3>Snapshot trigger</h3>
 *
 * <p>Each time a batch is written to the partition log, the caller must invoke {@link
 * #notifyBatchWritten(long)}. The manager counts batches written since the last snapshot. When the
 * count reaches the configured {@code intervalEntryCount}, a new snapshot is taken automatically.
 * Only one snapshot may be in progress at a time; a second trigger that arrives while a snapshot is
 * being persisted is silently deferred until the next qualifying batch.
 *
 * <h3>Snapshot format</h3>
 *
 * <p>The snapshot payload is a single file ({@code offsets.sbe}) written inside the RAFT snapshot
 * directory via {@link OffsetStore#saveToDirectory}. The SBE-encoded {@code OffsetSnapshotPayload}
 * message carries all committed consumer offsets for the partition.
 *
 * <h3>Recovery</h3>
 *
 * <p>On broker startup (or after a RAFT leader failover), call {@link #loadLatestSnapshot()} to
 * restore offset state from the most recent persisted snapshot. This method is safe to call from
 * any thread before the actor is started.
 *
 * <h3>Threading</h3>
 *
 * <p>All mutable state is accessed exclusively on this actor's thread. {@link
 * #notifyBatchWritten(long)} uses {@code actor.call()} to enqueue work; callers do not need to
 * synchronize.
 */
public final class SnapshotManager extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(SnapshotManager.class);

  private final int partitionId;
  private final int intervalEntryCount;
  private final ConstructableSnapshotStore snapshotStore;
  private final OffsetStore offsetStore;

  /** Number of batches written since the last successful snapshot. */
  private long entriesSinceLastSnapshot = 0;

  /**
   * Monotonically increasing log position used as the RAFT snapshot index. Updated on each
   * successful {@link #notifyBatchWritten(long)} call; also restored from the latest persisted
   * snapshot on {@link #loadLatestSnapshot()}.
   */
  private long lastLogPosition = 0;

  /**
   * RAFT term supplied by the caller via {@link #setCurrentTerm(long)}. Defaults to {@code 1} so
   * that snapshots can be taken even if no role-change notification has been delivered yet.
   */
  private long currentTerm = 1;

  /**
   * Guards against concurrent snapshot attempts. Reset to {@code false} when the in-progress
   * snapshot is persisted (or aborted on error).
   */
  private boolean snapshotInProgress = false;

  public SnapshotManager(
      final int partitionId,
      final int intervalEntryCount,
      final ConstructableSnapshotStore snapshotStore,
      final OffsetStore offsetStore) {
    this.partitionId = partitionId;
    this.intervalEntryCount = intervalEntryCount;
    this.snapshotStore = snapshotStore;
    this.offsetStore = offsetStore;
  }

  @Override
  public String getName() {
    return "event-bridge-snapshot-" + partitionId;
  }

  // -------------------------------------------------------------------------
  // Public API — called from external actors / components

  /**
   * Notifies the manager that a batch has been written to the partition log at the given {@code
   * logPosition}. Increments the counter and triggers a snapshot when the threshold is reached.
   *
   * <p>This method enqueues work onto this actor's queue and returns a future that completes once
   * the counter update (and any immediately initiated snapshot) has been processed. Callers may
   * ignore the returned future if fire-and-forget semantics are sufficient.
   *
   * @param logPosition the highest LogStream position produced by the batch
   * @return a future that completes when the notification has been processed on this actor's thread
   */
  public ActorFuture<Void> notifyBatchWritten(final long logPosition) {
    final var result = new CompletableActorFuture<Void>();
    actor.call(
        () -> {
          lastLogPosition = logPosition;
          entriesSinceLastSnapshot++;
          if (entriesSinceLastSnapshot >= intervalEntryCount && !snapshotInProgress) {
            triggerSnapshot();
          }
          result.complete(null);
        });
    return result;
  }

  /**
   * Updates the RAFT term tracked by this manager. Should be called whenever the local node's RAFT
   * role (and thus term) changes. The term is embedded in the RAFT snapshot ID.
   *
   * <p>Returns a future so callers can sequence a term update before the next batch notification,
   * guaranteeing the correct term is used in the immediately following snapshot.
   *
   * @param term the new RAFT term
   * @return a future that completes when the term has been recorded on this actor's thread
   */
  public ActorFuture<Void> setCurrentTerm(final long term) {
    final var result = new CompletableActorFuture<Void>();
    actor.call(
        () -> {
          currentTerm = term;
          result.complete(null);
        });
    return result;
  }

  /**
   * Loads offset state from the latest persisted RAFT snapshot into the provided {@link
   * OffsetStore}. Safe to call before the actor is started (accesses the snapshot store
   * synchronously on the calling thread).
   *
   * <p>A missing snapshot is treated as an empty store (no-op); IO errors are logged as warnings
   * and do not propagate.
   */
  public void loadLatestSnapshot() {
    final Optional<io.camunda.zeebe.snapshots.PersistedSnapshot> latest =
        snapshotStore.getLatestSnapshot();
    if (latest.isEmpty()) {
      LOG.debug(
          "No persisted snapshot found for partition {}; starting with empty offset state",
          partitionId);
      return;
    }
    final var snapshot = latest.get();
    try {
      offsetStore.loadFromDirectory(partitionId, snapshot.getPath());
      lastLogPosition = snapshot.getIndex();
      LOG.info(
          "Restored offset state for partition {} from snapshot at index {}",
          partitionId,
          snapshot.getIndex());
    } catch (final IOException e) {
      LOG.warn(
          "Failed to load offset snapshot for partition {} from {}; "
              + "starting with empty offset state",
          partitionId,
          snapshot.getPath(),
          e);
    }
  }

  // -------------------------------------------------------------------------
  // Snapshot lifecycle — runs on this actor's thread

  private void triggerSnapshot() {
    snapshotInProgress = true;
    LOG.debug(
        "Triggering snapshot for partition {} at index {} (term {})",
        partitionId,
        lastLogPosition,
        currentTerm);

    final var result =
        snapshotStore.newTransientSnapshot(lastLogPosition, currentTerm, 0, 0, false);

    if (result.isLeft()) {
      handleNewSnapshotError(result.getLeft());
      return;
    }

    final TransientSnapshot transientSnapshot = result.get();
    final ActorFuture<Void> takeFuture =
        transientSnapshot.take(
            snapshotDir -> {
              try {
                offsetStore.saveToDirectory(partitionId, snapshotDir);
              } catch (final IOException e) {
                throw new UncheckedIOException(
                    "Failed to write offset state to snapshot directory " + snapshotDir, e);
              }
            });

    actor.runOnCompletion(
        takeFuture,
        (ok, takeError) -> {
          if (takeError != null) {
            LOG.warn("Failed to take snapshot for partition {}; aborting", partitionId, takeError);
            actor.runOnCompletion(
                transientSnapshot.abort(), (v, abortErr) -> snapshotInProgress = false);
            return;
          }
          persistSnapshot(transientSnapshot);
        });
  }

  private void persistSnapshot(final TransientSnapshot transientSnapshot) {
    final ActorFuture<io.camunda.zeebe.snapshots.PersistedSnapshot> persistFuture =
        transientSnapshot.persist();

    actor.runOnCompletion(
        persistFuture,
        (snapshot, persistError) -> {
          snapshotInProgress = false;
          if (persistError != null) {
            LOG.warn(
                "Failed to persist snapshot for partition {}; will retry on next threshold",
                partitionId,
                persistError);
            // Do not reset the counter: the un-snapshotted entries remain; the next batch push
            // may cross the threshold again and retry.
          } else {
            entriesSinceLastSnapshot = 0;
            LOG.info(
                "Snapshot persisted for partition {} at index {} (snapshotId={})",
                partitionId,
                lastLogPosition,
                snapshot.getId());
          }
        });
  }

  private void handleNewSnapshotError(final SnapshotException error) {
    snapshotInProgress = false;
    if (error instanceof SnapshotException.SnapshotAlreadyExistsException) {
      // The snapshot store already holds a snapshot at this index. Log positions are strictly
      // monotonically increasing, so the next notifyBatchWritten call will supply a greater
      // position; resetting the counter is sufficient to prevent a spin-loop.
      entriesSinceLastSnapshot = 0;
      LOG.debug(
          "Snapshot already exists for partition {} at index {}; "
              + "will retry at a higher index on the next threshold",
          partitionId,
          lastLogPosition);
    } else {
      LOG.warn(
          "Failed to create transient snapshot for partition {} at index {}; "
              + "will retry on next threshold",
          partitionId,
          lastLogPosition,
          error);
    }
  }
}
