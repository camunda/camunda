/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.transfer;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.SnapshotChunkReader;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotChunkReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.agrona.CloseHelper;

public class SnapshotTransferServiceImpl implements SnapshotTransferService {

  private final ConstructableSnapshotStore snapshotStore;
  private final Map<UUID, PendingTransfer> pendingTransfers = new HashMap<>();
  private final int partitionId;

  public SnapshotTransferServiceImpl(
      final ConstructableSnapshotStore snapshotStore, final int partitionId) {
    this.snapshotStore = snapshotStore;
    this.partitionId = partitionId;
  }

  @Override
  public ActorFuture<SnapshotChunk> getLatestSnapshot(final int partition, final UUID transferId) {
    if (partition != partitionId) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalArgumentException(
              String.format(
                  "[%s] Invalid partition: %d. Current partition is %d",
                  transferId, partition, partitionId)));
    }
    final var lastSnapshot = snapshotStore.getLatestSnapshot();
    if (lastSnapshot.isEmpty()) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalArgumentException(String.format("[%s] No snapshot found", transferId)));
    }
    final var snapshotId = lastSnapshot.get().getId();
    try {
      final var reader = new FileBasedSnapshotChunkReader(lastSnapshot.get().getPath());
      final var transfer = new PendingTransfer(snapshotId, null, reader);
      final var chunk = transfer.next();
      if (chunk == null) {
        return CompletableActorFuture.completedExceptionally(
            new IllegalArgumentException(
                String.format("[%s] No snapshot chunk found", transferId)));
      }
      pendingTransfers.put(transferId, transfer);
      return CompletableActorFuture.completed(chunk);
    } catch (final IOException e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
  }

  @Override
  public ActorFuture<SnapshotChunk> getNextChunk(
      final int partition,
      final String snapshotId,
      final String previousChunkName,
      final UUID transferId) {
    final var transfer = pendingTransfers.get(transferId);
    if (transfer != null) {
      if (!transfer.snapshotId.equals(snapshotId)) {
        return CompletableActorFuture.completedExceptionally(
            new IllegalArgumentException(
                String.format(
                    "[%s] Invalid snapshotId: %s. Expected: %s",
                    transferId, snapshotId, transfer.snapshotId)));
      }
      if (!transfer.lastChunkName.equals(previousChunkName)) {
        return CompletableActorFuture.completedExceptionally(
            new IllegalArgumentException(
                String.format(
                    "[%s] Invalid previousChunkName: %s. Expected: %s",
                    transferId, previousChunkName, transfer.lastChunkName)));
      }
      return CompletableActorFuture.completed(transfer.next());
    } else {
      return CompletableActorFuture.completedExceptionally(
          new IllegalArgumentException(
              String.format("[%s] No transfer found for snapshotId %s", transferId, snapshotId)));
    }
  }

  private static class PendingTransfer {

    private final String snapshotId;
    private String lastChunkName;
    private final SnapshotChunkReader reader;

    PendingTransfer(
        final String snapshotId, final String lastChunkName, final SnapshotChunkReader reader) {
      this.snapshotId = snapshotId;
      this.lastChunkName = lastChunkName;
      this.reader = reader;
    }

    private SnapshotChunk next() {
      if (reader != null && reader.hasNext()) {
        final var next = reader.next();
        lastChunkName = next.getChunkName();
        return next;
      } else {
        CloseHelper.close(reader);
        return null;
      }
    }
  }
}
