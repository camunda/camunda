/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.broker.system.partitions.NoEntryAtSnapshotPosition;
import io.camunda.zeebe.broker.system.partitions.impl.SnapshotIdFinder.NextSnapshotId;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotAlreadyExistsException;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.stream.impl.state.DbLastProcessedPositionState;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.agrona.CloseHelper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Takes snapshots on a follower while the partition's state lives in a relational database, so the
 * follower can compact its log.
 *
 * <p>The follower keeps no state of its own; only the leader writes the shared state. A snapshot of
 * the shared state just names it, so the follower can take one without the leader's help. The
 * snapshot's processed position is the lower of what the shared state has processed and what this
 * follower has committed in its own log, so the snapshot always points at an entry in this
 * follower's log. Like the leader's snapshots, it keeps the records the exporters still need.
 *
 * <p>Unlike {@link AsyncSnapshotDirector}, it reports itself unhealthy as soon as one attempt
 * fails, for example when it can't reach the database, and healthy again once an attempt succeeds
 * or finds nothing to do.
 */
@NullMarked
public final class FollowerSnapshotDirector extends Actor implements HealthMonitorable {

  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;

  private final Supplier<Optional<ZeebeDb<ZbColumnFamilies>>> sharedState;
  private final LongSupplier committedPosition;
  private final SnapshotIdFinder snapshotIdFinder;
  private final ConstructableSnapshotStore snapshotStore;
  private final Callable<CompletableFuture<Void>> flushLog;
  private final Function<ZeebeDb<ZbColumnFamilies>, StatePositionSupplier> positionSupplierFactory;
  private final Duration snapshotPeriod;
  private final Set<FailureListener> listeners = new HashSet<>();

  @SuppressWarnings("java:S3077") // allow volatile here, health is immutable
  private volatile HealthReport healthReport;

  private @Nullable Attempt ongoingAttempt;

  /**
   * @param sharedState opens the partition's shared state for reading, or returns empty if the
   *     partition has no state yet; the director closes it after each attempt
   * @param committedPosition the position of the last record this follower has committed
   * @param flushLog flushes this follower's log, before a snapshot lets it be compacted
   * @param snapshotPeriod how often to take a snapshot
   */
  public FollowerSnapshotDirector(
      final PartitionId partitionId,
      final Supplier<Optional<ZeebeDb<ZbColumnFamilies>>> sharedState,
      final LongSupplier committedPosition,
      final AtomixRecordEntrySupplier entrySupplier,
      final ConstructableSnapshotStore snapshotStore,
      final Callable<CompletableFuture<Void>> flushLog,
      final Function<ZeebeDb<ZbColumnFamilies>, StatePositionSupplier> positionSupplierFactory,
      final Duration snapshotPeriod) {
    super("FollowerSnapshotDirector", partitionId);
    this.sharedState = sharedState;
    this.committedPosition = committedPosition;
    this.snapshotStore = snapshotStore;
    this.flushLog = flushLog;
    this.positionSupplierFactory = positionSupplierFactory;
    this.snapshotPeriod = snapshotPeriod;
    snapshotIdFinder = new SnapshotIdFinder(entrySupplier, snapshotStore);
    healthReport = HealthReport.healthy(this);
  }

  @Override
  protected void onActorStarting() {
    actor.runAtFixedRate(snapshotPeriod, this::trySnapshot);
  }

  @Override
  protected void onActorClosing() {
    final var attempt = ongoingAttempt;
    if (attempt != null) {
      ongoingAttempt = null;
      attempt.close(true);
    }
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    LOG.error(
        "No snapshot was taken. Will try to take a snapshot after the snapshot period {}.",
        snapshotPeriod,
        failure);
    markUnhealthy(failure);
  }

  @Override
  public String componentName() {
    return getName();
  }

  @Override
  public HealthReport getHealthReport() {
    return healthReport;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> listeners.add(failureListener));
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> listeners.remove(failureListener));
  }

  /**
   * Takes a snapshot now, independently of the scheduled ones.
   *
   * @return the new snapshot, or null if there was nothing to take a snapshot of
   */
  @VisibleForTesting
  ActorFuture<@Nullable PersistedSnapshot> takeSnapshot() {
    final var snapshot = new CompletableActorFuture<@Nullable PersistedSnapshot>();
    actor.run(() -> trySnapshot().onComplete(snapshot));
    return snapshot;
  }

  private ActorFuture<@Nullable PersistedSnapshot> trySnapshot() {
    if (ongoingAttempt != null) {
      LOG.debug("Already taking a snapshot, skipping this request for a new one");
      return CompletableActorFuture.completed(null);
    }

    final var attempt = new Attempt();
    ongoingAttempt = attempt;
    final var result = new CompletableActorFuture<@Nullable PersistedSnapshot>();
    snapshot(attempt)
        .onComplete(
            (snapshot, error) -> {
              ongoingAttempt = null;
              attempt.close(error != null);
              if (error == null) {
                markHealthy();
                result.complete(snapshot);
              } else {
                LOG.warn(
                    "Failed to take a snapshot of the shared state. Will try again after the snapshot period {}.",
                    snapshotPeriod,
                    error);
                markUnhealthy(error);
                result.completeExceptionally(error);
              }
            });
    return result;
  }

  private ActorFuture<@Nullable PersistedSnapshot> snapshot(final Attempt attempt) {
    try {
      return snapshotSharedState(attempt);
    } catch (final Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
  }

  private ActorFuture<@Nullable PersistedSnapshot> snapshotSharedState(final Attempt attempt) {
    final var state = sharedState.get();
    if (state.isEmpty()) {
      LOG.trace("Skipping the snapshot, because the partition has no state yet");
      return CompletableActorFuture.completed(null);
    }
    final var db = state.get();
    attempt.state = db;

    final var processedPosition =
        new DbLastProcessedPositionState(db, db.createContext())
            .getLastSuccessfulProcessedRecordPosition();
    final var snapshotPosition = Math.min(processedPosition, committedPosition.getAsLong());
    if (snapshotPosition <= 0) {
      LOG.debug("Skipping the snapshot, because nothing this follower committed was processed yet");
      return CompletableActorFuture.completed(null);
    }

    final var positions = positionSupplierFactory.apply(db);
    final NextSnapshotId snapshotId;
    try {
      snapshotId =
          snapshotIdFinder.find(
              snapshotPosition,
              positions.getLowestExportedPosition(),
              positions.getHighestBackupPosition());
    } catch (final NoEntryAtSnapshotPosition e) {
      LOG.debug("Skipping the snapshot, because {}", e.getMessage());
      return CompletableActorFuture.completed(null);
    }

    final var newSnapshot =
        snapshotStore.newTransientSnapshot(
            snapshotId.index(),
            snapshotId.term(),
            snapshotId.processedPosition(),
            snapshotId.exportedPosition(),
            false);
    if (newSnapshot.isLeft()) {
      final var error = newSnapshot.getLeft();
      if (error instanceof SnapshotAlreadyExistsException) {
        LOG.debug("Skipping the snapshot, because {}", error.getMessage());
        return CompletableActorFuture.completed(null);
      }
      return CompletableActorFuture.completedExceptionally(error);
    }

    final var snapshot =
        newSnapshot.get().withMaxExportedPosition(positions.getHighestExportedPosition());
    attempt.snapshot = snapshot;
    return snapshot
        .take(directory -> db.createSnapshot(directory.toFile()))
        .andThen(this::flushLog, actor)
        .andThen(() -> persist(snapshot), actor);
  }

  private ActorFuture<@Nullable Void> flushLog() {
    final var flushed = new CompletableActorFuture<@Nullable Void>();
    try {
      flushLog
          .call()
          .whenCompleteAsync(
              (ignored, error) -> {
                if (error != null) {
                  flushed.completeExceptionally(error);
                } else {
                  flushed.complete(null);
                }
              },
              actor);
    } catch (final Exception e) {
      flushed.completeExceptionally(e);
    }
    return flushed;
  }

  private ActorFuture<@Nullable PersistedSnapshot> persist(final TransientSnapshot snapshot) {
    final var persisted = new CompletableActorFuture<@Nullable PersistedSnapshot>();
    snapshot
        .persist()
        .onComplete(
            (persistedSnapshot, error) -> {
              switch (error) {
                case null -> persisted.complete(persistedSnapshot);
                case final SnapshotAlreadyExistsException alreadyExists -> {
                  LOG.debug("Snapshot {} already exists", snapshot, alreadyExists);
                  persisted.complete(null);
                }
                case final SnapshotNotFoundException notFound -> {
                  LOG.warn(
                      "Failed to persist snapshot {}. Nothing to worry about if a newer snapshot exists.",
                      snapshot,
                      notFound);
                  persisted.complete(null);
                }
                default -> persisted.completeExceptionally(error);
              }
            });
    return persisted;
  }

  private void markHealthy() {
    if (!healthReport.isHealthy()) {
      healthReport = HealthReport.healthy(this);
      listeners.forEach(listener -> listener.onRecovered(healthReport));
    }
  }

  private void markUnhealthy(final Throwable failure) {
    healthReport = HealthReport.unhealthy(this).withIssue(failure, Instant.now());
    listeners.forEach(listener -> listener.onFailure(healthReport));
  }

  /** What one attempt opened, so it can be cleaned up however the attempt ends. */
  private static final class Attempt {
    private @Nullable ZeebeDb<ZbColumnFamilies> state;
    private @Nullable TransientSnapshot snapshot;

    private void close(final boolean failed) {
      if (failed && snapshot != null) {
        snapshot.abort();
      }
      CloseHelper.quietClose(state);
    }
  }
}
