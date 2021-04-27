/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import io.zeebe.snapshots.ConstructableSnapshotStore;
import io.zeebe.snapshots.PersistedSnapshotStore;
import io.zeebe.snapshots.ReceivableSnapshotStore;
import io.zeebe.snapshots.ReceivableSnapshotStoreFactory;
import io.zeebe.snapshots.SnapshotStoreSupplier;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.SchedulingHints;
import java.nio.file.Path;
import org.agrona.IoUtil;
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
public final class FileBasedSnapshotStoreFactory
    implements SnapshotStoreSupplier, ReceivableSnapshotStoreFactory {
  public static final String SNAPSHOTS_DIRECTORY = "snapshots";
  public static final String PENDING_DIRECTORY = "pending";

  private final Int2ObjectHashMap<FileBasedSnapshotStore> partitionSnapshotStores =
      new Int2ObjectHashMap<>();
  private final ActorScheduler actorScheduler;
  private final int nodeId;

  public FileBasedSnapshotStoreFactory(final ActorScheduler actorScheduler, final int nodeId) {
    this.actorScheduler = actorScheduler;
    this.nodeId = nodeId;
  }

  @Override
  public ReceivableSnapshotStore createReceivableSnapshotStore(
      final Path root, final int partitionId) {
    final var snapshotDirectory = root.resolve(SNAPSHOTS_DIRECTORY);
    final var pendingDirectory = root.resolve(PENDING_DIRECTORY);

    IoUtil.ensureDirectoryExists(snapshotDirectory.toFile(), "Snapshot directory");
    IoUtil.ensureDirectoryExists(pendingDirectory.toFile(), "Pending snapshot directory");

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

  @Override
  public ConstructableSnapshotStore getConstructableSnapshotStore(final int partitionId) {
    return partitionSnapshotStores.get(partitionId);
  }

  @Override
  public ReceivableSnapshotStore getReceivableSnapshotStore(final int partitionId) {
    return partitionSnapshotStores.get(partitionId);
  }

  @Override
  public PersistedSnapshotStore getPersistedSnapshotStore(final int partitionId) {
    return partitionSnapshotStores.get(partitionId);
  }
}
