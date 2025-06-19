/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.transfer;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.impl.SnapshotMetrics;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class SnapshotTransferImpl extends Actor implements SnapshotTransfer {

  private final SnapshotTransferService service;
  private final ReceivableSnapshotStore snapshotStore;

  private final SnapshotMetrics snapshotMetrics;
  private final ConcurrentHashMap<UUID, CloseableSilently> timers = new ConcurrentHashMap<>();

  public SnapshotTransferImpl(
      final Function<ConcurrencyControl, SnapshotTransferService> service,
      final SnapshotMetrics snapshotMetrics,
      final ReceivableSnapshotStore snapshotStore) {
    this.snapshotMetrics = snapshotMetrics;
    this.snapshotStore = snapshotStore;
    this.service = service.apply(this);
  }

  @Override
  public ActorFuture<PersistedSnapshot> getLatestSnapshot(final int partitionId) {
    final var transferId = UUID.randomUUID();
    timers.put(transferId, snapshotMetrics.startTransferTimer(true));
    final var result =
        service
            // no requirements on last processing position
            .getLatestSnapshot(partitionId, -1, transferId)
            .andThen(
                snapshot -> {
                  if (snapshot == null) {
                    return CompletableActorFuture.completed(null);
                  }
                  return snapshotStore
                      .newReceivedSnapshot(snapshot.getSnapshotId())
                      .thenApply(
                          fbsnapshot -> {
                            fbsnapshot.apply(snapshot);
                            return new Tuple<>(snapshot, fbsnapshot);
                          });
                },
                actor)
            .andThen(
                tuple -> {
                  if (tuple == null) {
                    return CompletableActorFuture.completed(null);
                  }
                  final var future =
                      receiveAllChunks(partitionId, tuple.getLeft(), tuple.getRight(), transferId);
                  future.onError(error -> tuple.getRight().abort());
                  return future;
                },
                actor);
    result.onComplete(
        (ignored, error) ->
            Optional.ofNullable(timers.remove(transferId)).ifPresent(CloseableSilently::close));

    return result;
  }

  @VisibleForTesting
  SnapshotTransferService snapshotTransferService() {
    return service;
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
            actor);
  }
}
