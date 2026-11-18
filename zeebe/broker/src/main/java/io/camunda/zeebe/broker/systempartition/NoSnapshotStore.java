/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.systempartition;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotListener;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The system partition has no state machine yet (design Q17), thus it takes no snapshots and
 * receives none. Raft needs a snapshot store anyway.
 */
final class NoSnapshotStore implements ReceivableSnapshotStore {

  private final Path path;

  NoSnapshotStore(final Path path) {
    this.path = path;
  }

  @Override
  public boolean hasSnapshotId(final String id) {
    return false;
  }

  @Override
  public Optional<PersistedSnapshot> getLatestSnapshot() {
    return Optional.empty();
  }

  @Override
  public ActorFuture<Set<PersistedSnapshot>> getAvailableSnapshots() {
    return CompletableActorFuture.completed(Set.of());
  }

  @Override
  public ActorFuture<Long> getCompactionBound() {
    return CompletableActorFuture.completed(0L);
  }

  @Override
  public ActorFuture<Optional<String>> reserveLatestSnapshot(final long checkpointId) {
    return CompletableActorFuture.completed(Optional.empty());
  }

  @Override
  public ActorFuture<Void> reserveSnapshot(final long checkpointId, final String snapshotId) {
    return CompletableActorFuture.completedExceptionally(
        new SnapshotNotFoundException("Expected to reserve snapshot " + snapshotId));
  }

  @Override
  public ActorFuture<Optional<PersistedSnapshot>> getReservedSnapshot(
      final long checkpointId, final String snapshotId) {
    return CompletableActorFuture.completed(Optional.empty());
  }

  @Override
  public void releaseReservation(final long checkpointId, final String snapshotId) {}

  @Override
  public void releaseAllReservations() {}

  @Override
  public ActorFuture<Void> abortPendingSnapshots() {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Boolean> addSnapshotListener(final PersistedSnapshotListener listener) {
    return CompletableActorFuture.completed(true);
  }

  @Override
  public ActorFuture<Boolean> removeSnapshotListener(final PersistedSnapshotListener listener) {
    return CompletableActorFuture.completed(true);
  }

  @Override
  public long getCurrentSnapshotIndex() {
    return 0;
  }

  @Override
  public ActorFuture<Void> delete() {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public Path getPath() {
    return path;
  }

  @Override
  public ActorFuture<ReceivedSnapshot> newReceivedSnapshot(final String snapshotId) {
    return CompletableActorFuture.completedExceptionally(
        new UnsupportedOperationException("The system partition receives no snapshots"));
  }

  @Override
  public void close() {}

  @Override
  public Optional<PersistedSnapshot> getBootstrapSnapshot() {
    return Optional.empty();
  }

  @Override
  public ActorFuture<PersistedSnapshot> copyForBootstrap(
      final PersistedSnapshot persistedSnapshot, final BiConsumer<Path, Path> copySnapshot) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> deleteBootstrapSnapshots() {
    return CompletableActorFuture.completed();
  }
}
