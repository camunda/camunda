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
import io.camunda.zeebe.snapshots.BootstrapSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.SnapshotChunkReader;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotAlreadyExistsException;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotChunkReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.agrona.CloseHelper;

public class SnapshotTransferServiceImpl implements SnapshotTransferService {

  private final BootstrapSnapshotStore snapshotStore;
  private final Map<UUID, PendingTransfer> pendingTransfers = new HashMap<>();
  private final int partitionId;
  private final BiConsumer<Path, Path> copyForBootstrap;
  private final ConcurrencyControl concurrency;

  public SnapshotTransferServiceImpl(
      final BootstrapSnapshotStore snapshotStore,
      final int partitionId,
      final BiConsumer<Path, Path> copyForBootstrap,
      final ConcurrencyControl concurrency) {
    this.snapshotStore = snapshotStore;
    this.partitionId = partitionId;
    this.copyForBootstrap = copyForBootstrap;
    this.concurrency = concurrency;
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

    return getLatestSnapshotForBootstrap(transferId)
        .andThen(
            snapshot -> {
              final var snapshotId = snapshot.getId();
              try {
                final var reader = new FileBasedSnapshotChunkReader(snapshot.getPath());
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
            },
            concurrency);
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

  private ActorFuture<PersistedSnapshot> getLatestSnapshotForBootstrap(final UUID transferId) {
    final ActorFuture<PersistedSnapshot> lastSnapshotFuture = concurrency.createFuture();

    final var lastSnapshot = snapshotStore.getBootstrapSnapshot();
    if (lastSnapshot.isEmpty()) {
      createSnapshotForBootstrap(transferId).onComplete(lastSnapshotFuture, concurrency);
    } else {
      lastSnapshotFuture.complete(lastSnapshot.get());
    }
    return lastSnapshotFuture;
  }

  private ActorFuture<PersistedSnapshot> createSnapshotForBootstrap(final UUID transferId) {
    final var lastPersistedSnapshot = snapshotStore.getLatestSnapshot();
    if (lastPersistedSnapshot.isEmpty()) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalArgumentException(String.format("[%s] No snapshot found", transferId)));
    } else {
      return snapshotStore
          .copyForBootstrap(lastPersistedSnapshot.get(), copyForBootstrap)
          .andThen(
              (snapshot, error) -> {
                if (error != null) {
                  if (error instanceof SnapshotAlreadyExistsException) {
                    return CompletableActorFuture.completed(
                        snapshotStore.getBootstrapSnapshot().orElse(null));
                  } else {
                    return CompletableActorFuture.completedExceptionally(error);
                  }
                } else {
                  return CompletableActorFuture.completed(snapshot);
                }
              },
              concurrency);
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
      // reader.hasNext does not involve any I/O
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
