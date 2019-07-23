/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.snapshot;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.snapshot.impl.DefaultSnapshotRestoreRequest;
import io.zeebe.logstreams.state.SnapshotConsumer;
import io.zeebe.util.ZbLogger;
import io.zeebe.util.collection.Tuple;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class RestoreSnapshotReplicator {

  private final RestoreClient client;
  private final SnapshotRestoreContext restoreContext;
  private final Executor executor;
  private final Logger logger;
  private SnapshotConsumer snapshotConsumer;
  private int numChunks;

  public RestoreSnapshotReplicator(
      RestoreClient client,
      SnapshotRestoreContext restoreContext,
      SnapshotConsumer snapshotConsumer,
      Executor executor,
      Logger logger) {
    this.client = client;
    this.restoreContext = restoreContext;
    this.snapshotConsumer = snapshotConsumer;
    this.executor = executor;
    this.logger = logger;
  }

  public RestoreSnapshotReplicator(
      RestoreClient client,
      SnapshotRestoreContext restoreContext,
      SnapshotConsumer snapshotConsumer,
      Executor executor) {
    this(
        client,
        restoreContext,
        snapshotConsumer,
        executor,
        new ZbLogger(RestoreSnapshotReplicator.class));
  }

  public CompletableFuture<Tuple<Long, Long>> restore(
      MemberId server, long snapshotId, int numChunks) {
    this.numChunks = numChunks;
    final CompletableFuture<Tuple<Long, Long>> result = new CompletableFuture<>();

    if (restoreContext.getStateStorage().existSnapshot(snapshotId)) {
      result.complete(restoreContext.getSnapshotPositionSupplier().get());
    } else {
      restoreInternal(server, snapshotId, 0, result);
    }

    return result;
  }

  private void restoreInternal(
      MemberId server, long snapshotId, int chunkIdx, CompletableFuture<Tuple<Long, Long>> future) {
    final DefaultSnapshotRestoreRequest request =
        new DefaultSnapshotRestoreRequest(snapshotId, chunkIdx);
    client
        .requestSnapshotChunk(server, request)
        .whenCompleteAsync(
            (r, e) -> {
              if (e != null) {
                failReplication(snapshotId, future, e);
                return;
              } else if (!r.isSuccess()) {
                failReplication(
                    snapshotId,
                    future,
                    new RuntimeException(
                        String.format(
                            "Could not restore snapshot %d. Received an invalid response for request %d from server %s",
                            snapshotId, request.getChunkIdx(), server.id())));
                return;
              } else if (!snapshotConsumer.consumeSnapshotChunk(r.getSnapshotChunk())) {
                failReplication(
                    snapshotId,
                    future,
                    new RuntimeException(
                        String.format(
                            "Could not restore snapshot %d. Failed to consume snapshot chunk %d",
                            snapshotId, request.getChunkIdx())));
                return;
              }

              if (chunkIdx + 1 < numChunks) {
                restoreInternal(server, snapshotId, chunkIdx + 1, future);
                return;
              }

              if (snapshotConsumer.completeSnapshot(snapshotId)) {
                final Tuple<Long, Long> positions =
                    restoreContext.getSnapshotPositionSupplier().get();
                future.complete(positions);
              } else {
                failReplication(
                    snapshotId,
                    future,
                    new RuntimeException(
                        String.format(
                            "Could not restore snapshot %d. Failed to move valid snapshot.",
                            snapshotId)));
              }
            },
            executor);
  }

  private void failReplication(long snapshotId, CompletableFuture future, Throwable error) {
    future.completeExceptionally(error);
    logger.debug("Snapshot restore failed {}", snapshotId, error);
    snapshotConsumer.invalidateSnapshot(snapshotId);
  }
}
