/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.impl;

import io.atomix.cluster.MemberId;
import io.atomix.utils.concurrent.ThreadContext;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.RestoreInfoRequest;
import io.zeebe.distributedlog.restore.RestoreInfoResponse;
import io.zeebe.distributedlog.restore.RestoreNodeProvider;
import io.zeebe.distributedlog.restore.RestoreStrategy;
import io.zeebe.distributedlog.restore.log.LogReplicator;
import io.zeebe.distributedlog.restore.snapshot.RestoreSnapshotReplicator;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreStrategy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public class RestoreController {

  private final ThreadContext restoreThreadContext;
  private final Logger logger;
  private final RestoreClient restoreClient;
  private final RestoreNodeProvider nodeProvider;
  private final LogReplicator logReplicator;
  private final RestoreSnapshotReplicator snapshotReplicator;

  public RestoreController(
      RestoreClient restoreClient,
      RestoreNodeProvider nodeProvider,
      LogReplicator logReplicator,
      RestoreSnapshotReplicator snapshotReplicator,
      ThreadContext restoreThreadContext,
      Logger logger) {
    this.restoreClient = restoreClient;
    this.nodeProvider = nodeProvider;
    this.logReplicator = logReplicator;
    this.snapshotReplicator = snapshotReplicator;
    this.restoreThreadContext = restoreThreadContext;
    this.logger = logger;
  }

  public long restore(long latestLocalPosition, long backupPosition) {
    final RestoreInfoRequest request =
        new DefaultRestoreInfoRequest(latestLocalPosition, backupPosition);

    return findRestoreServer()
        .thenCompose(server -> requestRestoreStrategy(server, request))
        .thenCompose(RestoreStrategy::executeRestoreStrategy)
        .join();
  }

  private CompletableFuture<MemberId> findRestoreServer() {
    final CompletableFuture<MemberId> result = new CompletableFuture<>();
    tryFindRestoreServer(result);
    return result;
  }

  private void tryFindRestoreServer(CompletableFuture<MemberId> result) {
    final MemberId server = nodeProvider.provideRestoreNode();
    if (server != null) {
      result.complete(server);
    } else {
      restoreThreadContext.schedule(Duration.ofMillis(100), () -> tryFindRestoreServer(result));
    }
  }

  private CompletableFuture<RestoreStrategy> requestRestoreStrategy(
      MemberId server, RestoreInfoRequest request) {
    return restoreClient
        .requestRestoreInfo(server, request)
        .thenCompose(response -> onRestoreInfoReceived(server, request, response));
  }

  private CompletableFuture<RestoreStrategy> onRestoreInfoReceived(
      MemberId server, RestoreInfoRequest request, RestoreInfoResponse response) {
    final CompletableFuture<RestoreStrategy> result = new CompletableFuture<>();

    switch (response.getReplicationTarget()) {
      case SNAPSHOT:
        final SnapshotRestoreStrategy snapshotRestoreStrategy =
            new SnapshotRestoreStrategy(
                logReplicator,
                snapshotReplicator,
                response.getSnapshotRestoreInfo(),
                request.getLatestLocalPosition(),
                request.getBackupPosition(),
                server,
                logger);
        result.complete(snapshotRestoreStrategy);
        break;
      case EVENTS:
        logger.debug(
            "Restoring events {} - {} from server {}",
            request.getLatestLocalPosition(),
            request.getBackupPosition(),
            server);
        result.complete(
            () ->
                logReplicator.replicate(
                    server, request.getLatestLocalPosition(), request.getBackupPosition()));
        break;
      case NONE:
        logger.debug(
            "Restore server {} reports nothing to replicate for request {}", server, request);
        result.complete(() -> CompletableFuture.completedFuture(request.getLatestLocalPosition()));
        break;
    }

    return result;
  }
}
