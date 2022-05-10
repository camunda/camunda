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
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedLongSynchronizer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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

  private final Lock commitPositionLock = new ReentrantLock();

  @GuardedBy("commitPositionLock")
  private long commitPosition = -1;

  private final StreamProcessorMode processorMode;
  private final ScheduledExecutorService scheduledSnapshotExecutor =
      Executors.newSingleThreadScheduledExecutor();

  public ThreadSafeSnapshotDirector(
      final StateController stateController,
      final StreamProcessor streamProcessor,
      final Duration snapshotPeriod,
      final StreamProcessorMode processorMode) {
    this.stateController = stateController;
    this.streamProcessor = streamProcessor;
    this.processorMode = processorMode;
    final var firstSnapshotTime =
        RandomDuration.getRandomDurationMinuteBased(Duration.ofMinutes(1), snapshotPeriod);

    scheduledSnapshotExecutor.scheduleAtFixedRate(
        this::forceSnapshot,
        firstSnapshotTime.toSeconds(),
        snapshotPeriod.toSeconds(),
        TimeUnit.SECONDS);
  }

  @Override
  public Future<PersistedSnapshot> forceSnapshot() {
    return scheduledSnapshotExecutor.submit(this::forceSnapshotSync);
  }

  private PersistedSnapshot forceSnapshotSync() {
    LOG.info("Taking a new snapshot");
    final var lastProcessedPosition = streamProcessor.getLastProcessedPositionAsync().join();
    if (lastProcessedPosition == StreamProcessor.UNSET_POSITION) {
      LOG.info("Skipping snapshot, stream processor hasn't processed anything yet");
      return null;
    }

    LOG.info("Taking a transient snapshot for position {}", lastProcessedPosition);
    final var transientSnapshot =
        stateController.takeTransientSnapshot(lastProcessedPosition).join();
    LOG.info("Transient snapshot was taken");

    waitForSufficientCommitPosition();

    LOG.info("Persisting the transient snapshot");
    final var persistedSnapshot = transientSnapshot.persist().join();
    LOG.info("Snapshot was persisted: {}", persistedSnapshot);

    return persistedSnapshot;
  }

  private void waitForSufficientCommitPosition() {
    if (processorMode == StreamProcessorMode.REPLAY) {
      return;
    }

    final var lastWrittenPosition = streamProcessor.getLastWrittenPositionAsync().join();
    synchronized (commitPositionLock) {
      LOG.debug(
          "Waiting for commit position {} to include the last written position {} ",
          commitPosition,
          lastWrittenPosition);
      while (lastWrittenPosition < commitPosition) {
        try {
          commitPositionLock.wait();
        } catch (final InterruptedException e) {
          // retry
          // TODO: Or should we cancel the snapshot here? See also: the TODO on close()
        }
      }
      LOG.debug("Snapshot can now be persisted, last written position is committed");
    }
  }

  @Override
  public void onCommit(final IndexedRaftLogEntry indexedRaftLogEntry) {
    if (indexedRaftLogEntry.isApplicationEntry()) {
      final var newValue = indexedRaftLogEntry.getApplicationEntry().highestPosition();
      LOG.trace("Received a new commit position: {}", newValue);
      synchronized (commitPositionLock) {
        if (commitPosition < newValue) {
          commitPosition = newValue;
          commitPositionLock.notify();
        }
      }
    }
  }

  @Override
  public void close() throws Exception {
    // TODO: How do we cancel in-progress snapshots here?
    //  They could stall forever, waiting for a new commit position that'll never come.
    scheduledSnapshotExecutor.shutdownNow();
  }

  private static final class PositionAwaiter {
    private static final class Sync extends AbstractQueuedLongSynchronizer {
      private final long requestedPosition;

      Sync(final long requestedPosition) {
        this.requestedPosition = requestedPosition;
      }

      @Override
      protected long tryAcquireShared(final long arg) {
        return getState() > requestedPosition ? 1 : -1;
      }
    }
  }
}
