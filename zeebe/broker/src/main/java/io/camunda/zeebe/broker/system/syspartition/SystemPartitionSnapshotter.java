/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.raft.RaftCommitListener;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.storage.log.RaftLogReader;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotFileInfoProvider;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;

/**
 * Persists the {@link SystemPartitionState} as a single JSON file per snapshot, and restores it
 * from the latest snapshot on start.
 *
 * <p>For now it takes a single snapshot, at the latest committed index, once a partition without a
 * snapshot has committed. Taking further snapshots, and thereby compacting the log, follows once
 * the system partition replicates records that change its state.
 */
public final class SystemPartitionSnapshotter extends Actor implements RaftCommitListener {
  public static final String STATE_FILE = "system-partition.json";

  /**
   * Snapshots hold a single small JSON file, which the store checksums by reading it, so there is
   * no precomputed file information to provide.
   */
  static final SnapshotFileInfoProvider FILE_INFO_PROVIDER = path -> SnapshotFilesInfo.none();

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RaftPartition raftPartition;
  private final FileBasedSnapshotStore snapshotStore;
  private volatile SystemPartitionState state;
  private RaftLogReader reader;
  private boolean snapshotInProgress;

  SystemPartitionSnapshotter(
      final RaftPartition raftPartition, final FileBasedSnapshotStore snapshotStore) {
    super("SystemPartitionSnapshotter", SystemPartition.PARTITION_ID);
    this.raftPartition = raftPartition;
    this.snapshotStore = snapshotStore;
  }

  /** Returns the state restored from the latest snapshot, or the initial state. Thread-safe. */
  public SystemPartitionState state() {
    return state;
  }

  @Override
  protected void onActorStarting() {
    state =
        snapshotStore
            .getLatestSnapshot()
            .map(SystemPartitionSnapshotter::read)
            .orElseGet(SystemPartitionState::initial);
    reader = raftPartition.getServer().openReader();
    raftPartition.getServer().addCommitListener(this);
  }

  @Override
  protected void onActorStarted() {
    // The partition may have committed before this actor started, e.g. as leader of its initial
    // term, with no further commits to notify about.
    takeFirstSnapshot();
  }

  @Override
  protected void onActorClosing() {
    raftPartition.getServer().removeCommitListener(this);
    reader.close();
  }

  @Override
  public void onCommit(final long index) {
    // invoked on the Raft thread; take the snapshot on this actor instead
    actor.run(this::takeFirstSnapshot);
  }

  private void takeFirstSnapshot() {
    if (snapshotInProgress || snapshotStore.getLatestSnapshot().isPresent()) {
      return;
    }

    reader.seekToLast();
    if (!reader.hasNext()) {
      return;
    }

    final var entry = reader.next();
    final var stateToPersist = state;
    snapshotInProgress = true;
    // The system partition has no stream positions; the index stands in for them so snapshot ids
    // keep increasing with the log.
    snapshotStore
        .newTransientSnapshot(entry.index(), entry.term(), entry.index(), entry.index(), false)
        .ifRightOrLeft(
            snapshot ->
                actor.runOnCompletion(
                    snapshot.take(directory -> write(directory, stateToPersist)),
                    (taken, takeError) -> {
                      if (takeError != null) {
                        onSnapshotFailed(takeError);
                        return;
                      }
                      actor.runOnCompletion(
                          snapshot.persist(),
                          (persisted, persistError) -> {
                            if (persistError != null) {
                              onSnapshotFailed(persistError);
                            } else {
                              LOG.info("Persisted system partition snapshot {}", persisted.getId());
                            }
                          });
                    }),
            this::onSnapshotFailed);
  }

  private void onSnapshotFailed(final Throwable error) {
    snapshotInProgress = false;
    LOG.warn("Failed to take system partition snapshot, retrying on the next commit", error);
  }

  private static void write(final Path directory, final SystemPartitionState state) {
    try {
      Files.createDirectories(directory);
      MAPPER.writeValue(directory.resolve(STATE_FILE).toFile(), state);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static SystemPartitionState read(final PersistedSnapshot snapshot) {
    final SystemPartitionState state;
    try {
      state =
          MAPPER.readValue(
              snapshot.getPath().resolve(STATE_FILE).toFile(), SystemPartitionState.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    if (state.version() > SystemPartitionState.CURRENT_VERSION) {
      throw new IllegalStateException(
          "Expected system partition snapshot %s to have state version <= %d, but got %d"
              .formatted(snapshot.getId(), SystemPartitionState.CURRENT_VERSION, state.version()));
    }
    return state;
  }
}
