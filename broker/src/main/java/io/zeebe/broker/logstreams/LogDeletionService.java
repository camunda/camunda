/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.logstreams;

import io.zeebe.broker.Loggers;
import io.zeebe.snapshots.PersistedSnapshot;
import io.zeebe.snapshots.PersistedSnapshotListener;
import io.zeebe.snapshots.PersistedSnapshotStore;
import io.zeebe.util.sched.Actor;

public final class LogDeletionService extends Actor implements PersistedSnapshotListener {
  private final LogCompactor logCompactor;
  private final String actorName;
  private final PersistedSnapshotStore persistedSnapshotStore;

  public LogDeletionService(
      final int nodeId,
      final int partitionId,
      final LogCompactor logCompactor,
      final PersistedSnapshotStore persistedSnapshotStore) {
    this.persistedSnapshotStore = persistedSnapshotStore;
    this.logCompactor = logCompactor;
    actorName = buildActorName(nodeId, "DeletionService", partitionId);
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarting() {
    persistedSnapshotStore.addSnapshotListener(this);
  }

  @Override
  protected void onActorClosing() {
    if (persistedSnapshotStore != null) {
      persistedSnapshotStore.removeSnapshotListener(this);
    }
  }

  @Override
  public void onNewSnapshot(final PersistedSnapshot newPersistedSnapshot) {
    actor.run(() -> delegateDeletion(newPersistedSnapshot));
  }

  private void delegateDeletion(final PersistedSnapshot persistedSnapshot) {
    final var compactionBound = persistedSnapshot.getCompactionBound();
    logCompactor
        .compactLog(compactionBound)
        .exceptionally(error -> logCompactionError(compactionBound, error))
        .join();
  }

  private Void logCompactionError(final long compactionBound, final Throwable error) {
    if (error != null) {
      Loggers.DELETION_SERVICE.error(
          "Failed to compact Atomix log up to index {}", compactionBound, error);
    }

    return null;
  }
}
