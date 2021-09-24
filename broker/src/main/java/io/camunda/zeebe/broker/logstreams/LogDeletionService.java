/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.logstreams;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotListener;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.util.sched.Actor;
import java.util.Collection;
import java.util.Map;

public final class LogDeletionService extends Actor implements PersistedSnapshotListener {
  private final LogCompactor logCompactor;
  private final String actorName;
  private final Collection<PersistedSnapshotStore> persistedSnapshotStores;
  private final int partitionId;

  public LogDeletionService(
      final int nodeId,
      final int partitionId,
      final LogCompactor logCompactor,
      final Collection<PersistedSnapshotStore> persistedSnapshotStores) {
    this.persistedSnapshotStores = persistedSnapshotStores;
    this.logCompactor = logCompactor;
    actorName = buildActorName(nodeId, "DeletionService", partitionId);
    this.partitionId = partitionId;
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
    persistedSnapshotStores.forEach(store -> store.addSnapshotListener(this));
  }

  @Override
  protected void onActorClosing() {
    persistedSnapshotStores.forEach(store -> store.removeSnapshotListener(this));
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
