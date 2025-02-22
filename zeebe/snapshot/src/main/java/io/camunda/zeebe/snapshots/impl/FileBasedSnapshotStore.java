/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.CRC32CChecksumProvider;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotListener;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.RestorableSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotException;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FileBasedSnapshotStore extends Actor
    implements ConstructableSnapshotStore, ReceivableSnapshotStore, RestorableSnapshotStore {

  private final String actorName;
  private final int partitionId;
  private final FileBasedSnapshotStoreImpl snapshotStore;

  public FileBasedSnapshotStore(
      final int brokerId,
      final int partitionId,
      final Path root,
      final CRC32CChecksumProvider checksumProvider,
      final MeterRegistry meterRegistry) {
    actorName = buildActorName("SnapshotStore", partitionId);
    this.partitionId = partitionId;
    snapshotStore =
        new FileBasedSnapshotStoreImpl(
            brokerId, root, checksumProvider, actor, new SnapshotMetrics(meterRegistry));
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
    snapshotStore.start();
  }

  @Override
  protected void onActorClosing() {
    snapshotStore.close();
  }

  @Override
  public boolean hasSnapshotId(final String id) {
    return snapshotStore.hasSnapshotId(id);
  }

  @Override
  public Optional<PersistedSnapshot> getLatestSnapshot() {
    return snapshotStore.getLatestSnapshot();
  }

  @Override
  public ActorFuture<Set<PersistedSnapshot>> getAvailableSnapshots() {
    return snapshotStore.getAvailableSnapshots();
  }

  @Override
  public ActorFuture<Long> getCompactionBound() {
    return snapshotStore.getCompactionBound();
  }

  @Override
  public ActorFuture<Void> purgePendingSnapshots() {
    final CompletableActorFuture<Void> abortFuture = new CompletableActorFuture<>();
    return snapshotStore.purgePendingSnapshots();
  }

  @Override
  public ActorFuture<Boolean> addSnapshotListener(final PersistedSnapshotListener listener) {
    return snapshotStore.addSnapshotListener(listener);
  }

  @Override
  public ActorFuture<Boolean> removeSnapshotListener(final PersistedSnapshotListener listener) {
    return snapshotStore.removeSnapshotListener(listener);
  }

  @Override
  public long getCurrentSnapshotIndex() {
    return snapshotStore.getCurrentSnapshotIndex();
  }

  @Override
  public ActorFuture<Void> delete() {
    return snapshotStore.delete();
  }

  @Override
  public Path getPath() {
    return snapshotStore.getPath();
  }

  @Override
  public ActorFuture<FileBasedReceivedSnapshot> newReceivedSnapshot(final String snapshotId) {
    return snapshotStore.newReceivedSnapshot(snapshotId);
  }

  @Override
  public Either<SnapshotException, TransientSnapshot> newTransientSnapshot(
      final long index,
      final long term,
      final long processedPosition,
      final long exportedPosition) {
    return snapshotStore.newTransientSnapshot(index, term, processedPosition, exportedPosition);
  }

  @Override
  public void restore(final String snapshotId, final Map<String, Path> snapshotFiles)
      throws IOException {
    snapshotStore.restore(snapshotId, snapshotFiles);
  }

  @Override
  public String toString() {
    return "FileBasedSnapshotStore{"
        + "actorName='"
        + actorName
        + '\''
        + ", partitionId="
        + partitionId
        + ", snapshotStore="
        + snapshotStore
        + '}';
  }
}
