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
import io.camunda.zeebe.util.sched.ActorCompatability;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import org.slf4j.Logger;

@ThreadSafe
public class ThreadSafeSnapshotDirector
    implements AutoCloseable, SnapshotDirector, RaftCommittedEntryListener {
  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;

  @GuardedBy("this")
  private final StateController stateController;

  @GuardedBy("this")
  private final StreamProcessor streamProcessor;

  @GuardedBy("this")
  private volatile long commitPosition = -1;

  private final StreamProcessorMode processorMode;
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  private final ActorCompatability actorCompatability;

  public ThreadSafeSnapshotDirector(
      final ActorCompatability actorCompatability,
      final StateController stateController,
      final StreamProcessor streamProcessor,
      final StreamProcessorMode processorMode,
      final Duration snapshotPeriod) {
    this.actorCompatability = actorCompatability;
    this.stateController = stateController;
    this.streamProcessor = streamProcessor;
    this.processorMode = processorMode;

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
    return executor.submit(this::forceSnapshotSync);
  }

  /**
   * Technically this doesn't need to be synchronized but if we ever change the executor where this
   * is scheduled on, this will ensure that only one snapshot can be taken concurrently.
   *
   * @return the persisted snapshot or null if the snapshot was skipped
   */
  private synchronized PersistedSnapshot forceSnapshotSync() {
    LOG.info("Taking a new snapshot");

    final var lastProcessedPosition = streamProcessor.getLastProcessedPositionAsync().join();

    if (lastProcessedPosition == StreamProcessor.UNSET_POSITION) {
      LOG.info("Skipping snapshot, stream processor hasn't processed anything yet");
      return null;
    }

    LOG.info("Taking a transient snapshot for position {}", lastProcessedPosition);
    final var transientSnapshot =
        actorCompatability
            .await(() -> stateController.takeTransientSnapshot(lastProcessedPosition))
            .join();
    LOG.info("Transient snapshot was taken");

    waitForSufficientCommitPosition();

    LOG.info("Persisting the transient snapshot");
    final var persistedSnapshot = actorCompatability.await(transientSnapshot::persist).join();
    LOG.info("Snapshot was persisted: {}", persistedSnapshot);

    return persistedSnapshot;
  }

  private synchronized void waitForSufficientCommitPosition() {
    if (processorMode == StreamProcessorMode.REPLAY) {
      return;
    }

    final var lastWrittenPosition = streamProcessor.getLastWrittenPositionAsync().join();
    LOG.debug(
        "Waiting for commit position {} to include the last written position {} ",
        commitPosition,
        lastWrittenPosition);

    while (commitPosition < lastWrittenPosition) {
      try {
        wait(); // until onCommit notifies us
      } catch (final InterruptedException e) {
        throw new RuntimeException("Snapshot cancelled");
      }
    }
    LOG.debug("Snapshot can now be persisted, last written position is committed");
  }

  /**
   * This needs to be synchronized because we want to notify other threads that are waiting in
   * waitForSufficientCommitPosition that a new commit position is available.
   *
   * @param indexedRaftLogEntry the new committed entry
   */
  @Override
  public synchronized void onCommit(final IndexedRaftLogEntry indexedRaftLogEntry) {
    if (indexedRaftLogEntry.isApplicationEntry()) {
      final var newValue = indexedRaftLogEntry.getApplicationEntry().highestPosition();
      LOG.trace("Received a new commit position: {}", newValue);
      commitPosition = newValue;
      notify();
    }
  }

  @Override
  public void close() throws Exception {
    executor.shutdownNow();
  }
}
