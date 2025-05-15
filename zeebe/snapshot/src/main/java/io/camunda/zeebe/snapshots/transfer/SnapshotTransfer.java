/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.transfer;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.UUID;

public class SnapshotTransfer {

  private final SnapshotTransferService service;
  private final ReceivableSnapshotStore snapshotStore;
  private final ConcurrencyControl concurrency;

  public SnapshotTransfer(
      final SnapshotTransferService service,
      final ReceivableSnapshotStore snapshotStore,
      final ConcurrencyControl concurrency) {
    this.service = service;
    this.snapshotStore = snapshotStore;
    this.concurrency = concurrency;
  }

  public ActorFuture<PersistedSnapshot> getLatestSnapshot(final int partitionId) {
    final var transferId = UUID.randomUUID();
    return service
        .getLatestSnapshot(partitionId, transferId)
        .andThen(
            snapshot -> {
              if (snapshot == null) {
                return CompletableActorFuture.completedExceptionally(
                    new IllegalArgumentException(
                        "No initial chunk for latest snapshot in partition " + partitionId));
              }
              return snapshotStore
                  .newReceivedSnapshot(snapshot.getSnapshotId())
                  .thenApply(
                      fbsnapshot -> {
                        fbsnapshot.apply(snapshot);
                        return new Tuple<>(snapshot, fbsnapshot);
                      });
            },
            concurrency)
        .andThen(
            tuple -> {
              final var future =
                  receiveAllChunks(partitionId, tuple.getLeft(), tuple.getRight(), transferId);
              future.onError(error -> tuple.getRight().abort());
              return future;
            },
            concurrency);
  }

  private ActorFuture<PersistedSnapshot> receiveAllChunks(
      final int partitionId,
      final SnapshotChunk snapshotChunk,
      final ReceivedSnapshot receivedSnapshot,
      final UUID transferId) {
    return service
        .getNextChunk(
            partitionId,
            receivedSnapshot.snapshotId().getSnapshotIdAsString(),
            snapshotChunk.getChunkName(),
            transferId)
        .andThen(
            chunk -> {
              if (chunk != null) {
                receivedSnapshot.apply(chunk);
                return receiveAllChunks(partitionId, chunk, receivedSnapshot, transferId);
              } else {
                return receivedSnapshot.persist();
              }
            },
            concurrency);
  }
}
