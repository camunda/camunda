/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.zeebe.snapshots.transfer;

import static io.camunda.zeebe.util.Unit.unit;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.SnapshotChunkReader;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotAlreadyExistsException;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotChunkReader;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.agrona.CloseHelper;
import org.jspecify.annotations.Nullable;

public class SnapshotTransferServiceImpl implements SnapshotSenderService {

  private final PersistedSnapshotStore snapshotStore;
  private final Map<UUID, PendingTransfer> pendingTransfers = new HashMap<>();
  private final TakeSnapshot takeSnapshot;
  private final int partitionId;
  private final BiConsumer<Path, Path> copyForBootstrap;
  private final ConcurrencyControl concurrency;

  public SnapshotTransferServiceImpl(
      final PersistedSnapshotStore snapshotStore,
      final TakeSnapshot takeSnapshot,
      final int partitionId,
      final BiConsumer<Path, Path> copyForBootstrap,
      final ConcurrencyControl concurrency) {
    this.snapshotStore = snapshotStore;
    this.takeSnapshot = takeSnapshot;
    this.partitionId = partitionId;
    this.copyForBootstrap = copyForBootstrap;
    this.concurrency = concurrency;
  }

  @Override
  public ActorFuture<@Nullable SnapshotChunk> getLatestSnapshot(
      final int partition, final long lastProcessedPosition, final UUID transferId) {
    if (partition != partitionId) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalArgumentException(
              String.format(
                  "[%s] Invalid partition: %d. Current partition is %d",
                  transferId, partition, partitionId)));
    }

    return getLatestSnapshotForBootstrap(lastProcessedPosition)
        .andThen(
            snapshot -> {
              if (snapshot == null) {
                return CompletableActorFuture.completed(null);
              }
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
  public ActorFuture<@Nullable SnapshotChunk> getNextChunk(
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
      if (!Objects.equals(transfer.lastChunkName, previousChunkName)) {
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

  @Override
  public ActorFuture<Void> deleteSnapshots(final int partitionId) {
    if (partitionId != this.partitionId) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalArgumentException(
              String.format(
                  "Invalid partition: %d. Current partition is %d",
                  partitionId, this.partitionId)));
    }
    return snapshotStore
        .deleteBootstrapSnapshots()
        .thenApply(
            ignored -> {
              pendingTransfers.clear();
              return unit();
            });
  }

  private ActorFuture<@Nullable PersistedSnapshot> getLatestSnapshotForBootstrap(
      final long lastProcessedPosition) {
    return snapshotStore
        .getBootstrapSnapshot()
        .<ActorFuture<@Nullable PersistedSnapshot>>map(
            snapshot -> CompletableActorFuture.completed(snapshot).asNullable())
        .orElseGet(() -> createSnapshotForBootstrap(lastProcessedPosition));
  }

  private ActorFuture<PersistedSnapshot> createSnapshotForBootstrap(
      final long lastProcessedPosition) {
    final var lastSnapshot =
        snapshotStore
            .getLatestSnapshot()
            .filter(snapshot -> metadataProcessedPosition(snapshot) >= lastProcessedPosition)
            .<ActorFuture<PersistedSnapshot>>map(CompletableActorFuture::completed)
            .orElseGet(() -> takeSnapshot.takeSnapshot(lastProcessedPosition));

    return lastSnapshot.andThen(
        persistedSnapshot ->
            withReservation(
                persistedSnapshot,
                () ->
                    snapshotStore
                        .copyForBootstrap(persistedSnapshot, copyForBootstrap)
                        .andThen(
                            (snapshot, error) -> {
                              if (error != null) {
                                if (error instanceof SnapshotAlreadyExistsException) {
                                  return snapshotStore
                                      .getBootstrapSnapshot()
                                      .map(CompletableActorFuture::completed)
                                      .orElse(
                                          CompletableActorFuture.completedExceptionally(
                                              new IllegalStateException(
                                                  "Expected to find a bootstrap snapshot, but none found.")));
                                } else {
                                  return CompletableActorFuture.completedExceptionally(error);
                                }
                              } else {
                                return CompletableActorFuture.completed(snapshot);
                              }
                            },
                            concurrency)),
        concurrency);
  }

  private static long metadataProcessedPosition(final PersistedSnapshot snapshot) {
    final var metadata = snapshot.getMetadata();
    if (metadata == null) {
      throw new NullPointerException();
    }
    return metadata.processedPosition();
  }

  /**
   * Run a () -> ActorFuture<A> while the reservation of the persisted snapshot is active. Ensure
   * that the reservation is released even in case of errors
   */
  @VisibleForTesting
  <A extends @Nullable Object> ActorFuture<A> withReservation(
      final PersistedSnapshot persistedSnapshot, final Supplier<ActorFuture<A>> supplier) {
    return persistedSnapshot
        .reserve()
        .andThen(
            reservation -> {
              try {
                return supplier
                    .get()
                    .andThen(
                        (result, error) ->
                            // release even if an error happens
                            reservation
                                .release()
                                .andThen(
                                    ignored -> {
                                      if (error != null) {
                                        return CompletableActorFuture.completedExceptionally(error);
                                      } else {
                                        return CompletableActorFuture.completed(result);
                                      }
                                    },
                                    concurrency),
                        concurrency);
              } catch (final Exception e) {
                reservation.release();
                return CompletableActorFuture.completedExceptionally(e);
              }
            },
            concurrency);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    return deleteSnapshots(partitionId);
  }

  private static class PendingTransfer {

    private final String snapshotId;
    private @Nullable String lastChunkName;
    private final SnapshotChunkReader reader;

    PendingTransfer(
        final String snapshotId,
        final @Nullable String lastChunkName,
        final SnapshotChunkReader reader) {
      this.snapshotId = snapshotId;
      this.lastChunkName = lastChunkName;
      this.reader = reader;
    }

    private @Nullable SnapshotChunk next() {
      // reader.hasNext does not involve any I/O
      if (reader.hasNext()) {
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
