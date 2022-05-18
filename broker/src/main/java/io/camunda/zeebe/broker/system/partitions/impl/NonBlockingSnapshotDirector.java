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
import io.camunda.zeebe.broker.system.partitions.SnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.sched.ActorCompatability;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.ThreadSafe;
import org.slf4j.Logger;

@ThreadSafe
public class NonBlockingSnapshotDirector
    implements AutoCloseable, SnapshotDirector, RaftCommittedEntryListener {
  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;

  private final StateController stateController;

  private final StreamProcessor streamProcessor;

  private final StreamProcessorMode processorMode;

  // We currently use this executor only for scheduling snapshots at fixed rate. Since there are no
  // shared mutable state, there is no need to use an executor to serialize the task. If we want to
  // prevent concurrent snapshot, we can add a lock.
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  private final ActorCompatability actorCompatability;

  private final CommitAwaiter commitAwaiter;

  public NonBlockingSnapshotDirector(
      final ActorCompatability actorCompatability,
      final StateController stateController,
      final StreamProcessor streamProcessor,
      final StreamProcessorMode processorMode,
      final Duration snapshotPeriod,
      final CommitAwaiter commitAwaiter) {
    this.actorCompatability = actorCompatability;
    this.stateController = stateController;
    this.streamProcessor = streamProcessor;
    this.processorMode = processorMode;
    this.commitAwaiter = commitAwaiter;

    final var firstSnapshotTime =
        RandomDuration.getRandomDurationMinuteBased(Duration.ofMinutes(1), snapshotPeriod);

    executor.scheduleWithFixedDelay(
        this::forceSnapshotSync,
        firstSnapshotTime.toSeconds(),
        snapshotPeriod.toSeconds(),
        TimeUnit.SECONDS);
  }

  @Override
  public Future<PersistedSnapshot> forceSnapshot() {
    return forceSnapshotSync();
  }

  /**
   * Technically this doesn't need to be synchronized but if we ever change the executor where this
   * is scheduled on, this will ensure that only one snapshot can be taken concurrently.
   *
   * @return the persisted snapshot or null if the snapshot was skipped
   */
  private CompletableFuture<PersistedSnapshot> forceSnapshotSync() {
    LOG.info("Taking a new snapshot");

    return actorCompatability
        .await(streamProcessor::getLastProcessedPositionAsync)
        // Have to use Async because otherwise it might be executed on the caller thread (in the
        // actor which completes this future). Optionally, we can also pass this.executor to force
        // it to execute on this executor. However, since there are no shared mutable objects, there
        // is no need to force it to execute on this.executor.
        .thenComposeAsync(
            (lastProcessedPosition) -> {
              if (lastProcessedPosition == StreamProcessor.UNSET_POSITION) {
                LOG.info("Skipping snapshot, stream processor hasn't processed anything yet");
                return CompletableFuture.completedFuture(null);
              } else {
                return takeTransientSnapshot(lastProcessedPosition)
                    .thenComposeAsync(this::persistSnapshot);
              }
            });
  }

  private CompletableFuture<TransientSnapshot> takeTransientSnapshot(
      final Long lastProcessedPosition) {
    return actorCompatability.await(
        () -> stateController.takeTransientSnapshot(lastProcessedPosition));
  }

  private CompletableFuture<Void> waitForCommitPosition() {
    if (processorMode == StreamProcessorMode.REPLAY) {
      return CompletableFuture.completedFuture(null);
    }
    return actorCompatability
        .await(streamProcessor::getLastWrittenPositionAsync)
        .thenComposeAsync(commitAwaiter::waitForCommitPosition);
  }

  private CompletableFuture<PersistedSnapshot> persistSnapshot(
      final TransientSnapshot transientSnapshot) {
    return waitForCommitPosition()
        .thenCompose((ignored) -> actorCompatability.await(transientSnapshot::persist))
        .whenComplete(
            ((persistedSnapshot, throwable) -> {
              if (throwable != null) {
                transientSnapshot.abort();
              }
            }));
  }

  /**
   * This needs to be synchronized because we want to notify other threads that are waiting in
   * waitForSufficientCommitPosition that a new commit position is available.
   *
   * @param indexedRaftLogEntry the new committed entry
   */
  @Override
  public synchronized void onCommit(final IndexedRaftLogEntry indexedRaftLogEntry) {
    commitAwaiter.onCommit(indexedRaftLogEntry);
  }

  @Override
  public void close() throws Exception {
    executor.shutdownNow();
  }
}
