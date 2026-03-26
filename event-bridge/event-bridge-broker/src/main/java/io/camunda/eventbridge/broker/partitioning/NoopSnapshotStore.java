/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotListener;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Snapshot store that never creates or receives snapshots. Raft retains the full log.
 *
 * <p>TODO: Replace with {@code EventBridgeSnapshotStore} once consumer offset tracking is
 * implemented. That will enable log compaction based on the minimum committed consumer offset.
 */
public final class NoopSnapshotStore extends Actor implements ReceivableSnapshotStore {

  private final int partitionId;

  public NoopSnapshotStore(final int partitionId) {
    this.partitionId = partitionId;
  }

  @Override
  public ActorFuture<? extends ReceivedSnapshot> newReceivedSnapshot(final String snapshotId) {
    throw new UnsupportedOperationException(
        "EventBridge partition " + partitionId + " does not support snapshots yet");
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
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Long> getCompactionBound() {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> abortPendingSnapshots() {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Boolean> addSnapshotListener(final PersistedSnapshotListener listener) {
    return CompletableActorFuture.completed(false);
  }

  @Override
  public ActorFuture<Boolean> removeSnapshotListener(final PersistedSnapshotListener listener) {
    return CompletableActorFuture.completed(false);
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
    return null;
  }

  @Override
  public Optional<PersistedSnapshot> getBootstrapSnapshot() {
    return Optional.empty();
  }

  @Override
  public ActorFuture<PersistedSnapshot> copyForBootstrap(
      final PersistedSnapshot persistedSnapshot, final BiConsumer<Path, Path> copySnapshot) {
    return null;
  }

  @Override
  public ActorFuture<Void> deleteBootstrapSnapshots() {
    return null;
  }

  @Override
  public String getName() {
    return "NoopSnapshotStore-" + partitionId;
  }

  @Override
  public void close() {
    // nothing to close
  }
}
