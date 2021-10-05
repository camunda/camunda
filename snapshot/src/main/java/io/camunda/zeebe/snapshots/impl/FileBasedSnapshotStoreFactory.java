/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStoreFactory;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.SchedulingHints;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.agrona.collections.Int2ObjectHashMap;

/**
 * Loads existing snapshots in memory, cleaning out old and/or invalid snapshots if present.
 *
 * <p>The current load strategy is to lookup all files directly under the {@code
 * SNAPSHOTS_DIRECTORY}, try to extract {@link FileBasedSnapshotMetadata} from them, and if not
 * possible skip them (and print out a warning).
 *
 * <p>The metadata extraction is done by parsing the directory name using '%d-%d-%d-%d', where in
 * order we expect: index, term, processed position and exported position.
 */
public final class FileBasedSnapshotStoreFactory implements ReceivableSnapshotStoreFactory {
  public static final String SNAPSHOTS_DIRECTORY = "snapshots";
  public static final String PENDING_DIRECTORY = "pending";

  private final Int2ObjectHashMap<FileBasedSnapshotStore> partitionSnapshotStores =
      new Int2ObjectHashMap<>();
  private final ActorSchedulingService actorScheduler;
  private final int nodeId;

  public FileBasedSnapshotStoreFactory(
      final ActorSchedulingService actorScheduler, final int nodeId) {
    this.actorScheduler = actorScheduler;
    this.nodeId = nodeId;
  }

  @Override
  public ReceivableSnapshotStore createReceivableSnapshotStore(
      final Path root, final int partitionId) {
    final var snapshotDirectory = root.resolve(SNAPSHOTS_DIRECTORY);
    final var pendingDirectory = root.resolve(PENDING_DIRECTORY);

    try {
      FileUtil.ensureDirectoryExists(snapshotDirectory);
      FileUtil.ensureDirectoryExists(pendingDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create snapshot directories", e);
    }

    return partitionSnapshotStores.computeIfAbsent(
        partitionId,
        p -> createAndOpenNewSnapshotStore(partitionId, snapshotDirectory, pendingDirectory));
  }

  private FileBasedSnapshotStore createAndOpenNewSnapshotStore(
      final int partitionId, final Path snapshotDirectory, final Path pendingDirectory) {
    final var snapshotStore =
        new FileBasedSnapshotStore(
            nodeId,
            partitionId,
            new SnapshotMetrics(Integer.toString(partitionId)),
            snapshotDirectory,
            pendingDirectory);
    actorScheduler.submitActor(snapshotStore, SchedulingHints.ioBound()).join();
    return snapshotStore;
  }

  public ConstructableSnapshotStore getConstructableSnapshotStore(final int partitionId) {
    return partitionSnapshotStores.get(partitionId);
  }

  public ReceivableSnapshotStore getReceivableSnapshotStore(final int partitionId) {
    return partitionSnapshotStores.get(partitionId);
  }

  public PersistedSnapshotStore getPersistedSnapshotStore(final int partitionId) {
    return partitionSnapshotStores.get(partitionId);
  }

  /**
   * Return the same concurrent control (actor) that is used by the snapshot store of the given
   * partition
   *
   * @param partitionId
   * @return concurrency control
   */
  @Deprecated // This is an intermediate solution to run StateController and SnapshotStore on same
  // actor.
  public ConcurrencyControl getSnapshotStoreConcurrencyControl(final int partitionId) {
    return partitionSnapshotStores.get(partitionId);
  }
}
