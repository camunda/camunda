/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.logstreams;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotListener;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import java.util.Map;
import java.util.Set;

public final class LogDeletionService extends Actor implements PersistedSnapshotListener {
  private final LogCompactor logCompactor;
  private final String actorName;
  private final PersistedSnapshotStore persistedSnapshotStore;
  private final int partitionId;

  public LogDeletionService(
      final int nodeId,
      final int partitionId,
      final LogCompactor logCompactor,
      final PersistedSnapshotStore persistedSnapshotStore) {
    this.persistedSnapshotStore = persistedSnapshotStore;
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
    persistedSnapshotStore.addSnapshotListener(this);
  }

  @Override
  protected void onActorClosing() {
    persistedSnapshotStore.removeSnapshotListener(this);
  }

  @Override
  public void onNewSnapshot(final PersistedSnapshot newPersistedSnapshot) {
    actor.run(
        () ->
            persistedSnapshotStore
                .getAvailableSnapshots()
                .onComplete(
                    (availableSnapshots, error) -> {
                      if (error == null) {
                        delegateDeletion(getCompactionBoundOfOldestSnapshot(availableSnapshots));
                      } else {
                        Loggers.DELETION_SERVICE.error(
                            "Expected to compact logs, but could not get list of available snapshots.",
                            error);
                      }
                    }));
  }

  private long getCompactionBoundOfOldestSnapshot(final Set<PersistedSnapshot> availableSnapshots) {
    return availableSnapshots.stream()
        .map(PersistedSnapshot::getCompactionBound)
        .min(Long::compareTo)
        .orElse(0L);
  }

  private void delegateDeletion(final long compactionBound) {
    if (compactionBound <= 0) {
      return;
    }
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
