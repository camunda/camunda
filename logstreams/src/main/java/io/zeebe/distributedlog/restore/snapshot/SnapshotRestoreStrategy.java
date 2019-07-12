/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.snapshot;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.RestoreStrategy;
import io.zeebe.distributedlog.restore.log.LogReplicator;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public class SnapshotRestoreStrategy implements RestoreStrategy {

  private final MemberId server;
  private final LogReplicator logReplicator;
  private final Logger logger;
  private final long backupPosition;
  private final SnapshotRestoreInfo snapshotRestoreInfo;
  private final long latestLocalPosition;
  private final RestoreSnapshotReplicator replicator;

  public SnapshotRestoreStrategy(
      LogReplicator logReplicator,
      RestoreSnapshotReplicator replicator,
      SnapshotRestoreInfo snapshotRestoreInfo,
      long latestLocalPosition,
      long backupPosition,
      MemberId server,
      Logger logger) {
    this.logReplicator = logReplicator;
    this.replicator = replicator;
    this.snapshotRestoreInfo = snapshotRestoreInfo;
    this.latestLocalPosition = latestLocalPosition;
    this.backupPosition = backupPosition;
    this.server = server;
    this.logger = logger;
  }

  @Override
  public CompletableFuture<Long> executeRestoreStrategy() {
    logger.debug(
        "Restoring snapshot {} from server {} (expecting {} chunks)",
        snapshotRestoreInfo.getSnapshotId(),
        server,
        snapshotRestoreInfo.getNumChunks());

    return replicator
        .restore(server, snapshotRestoreInfo.getSnapshotId(), snapshotRestoreInfo.getNumChunks())
        .thenCompose(tuple -> onSnapshotsReplicated(tuple.getLeft(), tuple.getRight()));
  }

  private CompletableFuture<Long> onSnapshotsReplicated(
      long exporterPosition, long processedPosition) {
    final long fromPosition =
        Math.max(
            latestLocalPosition, // if exporter position is behind latestLocalPosition
            getFirstEventToBeReplicated(exporterPosition, processedPosition));
    final long toPosition = Math.max(processedPosition, backupPosition);
    // TODO: logstream.deleteAll(). https://github.com/zeebe-io/zeebe/issues/2509

    logger.debug(
        "Restored snapshot {} from server {}; restoring events from {} to {}",
        snapshotRestoreInfo.getSnapshotId(),
        server,
        fromPosition,
        toPosition);
    return logReplicator.replicate(
        server, fromPosition, toPosition, fromPosition > latestLocalPosition);
  }

  private long getFirstEventToBeReplicated(long exporterPosition, long processedPosition) {
    return Math.min(processedPosition, exporterPosition);
  }
}
