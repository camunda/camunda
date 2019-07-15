/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.impl;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.RestoreInfoRequest;
import io.zeebe.distributedlog.restore.RestoreInfoResponse;
import io.zeebe.distributedlog.restore.log.LogReplicationRequest;
import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import io.zeebe.distributedlog.restore.log.impl.DefaultLogReplicationRequestHandler;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreRequest;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreResponse;
import io.zeebe.distributedlog.restore.snapshot.impl.DefaultSnapshotRequestHandler;
import io.zeebe.distributedlog.restore.snapshot.impl.InvalidSnapshotRestoreResponse;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.StateSnapshotController;
import java.util.concurrent.CompletableFuture;
import org.slf4j.helpers.NOPLogger;

public class ReplicatingRestoreClient implements RestoreClient {

  private final StateSnapshotController replicatorSnapshotController;
  private final LogStream serverLogstream;
  private CompletableFuture<RestoreInfoResponse> restoreInfoResponse = new CompletableFuture<>();
  private boolean autoResponse = true;
  private boolean failSnapshotChunk = false;

  public ReplicatingRestoreClient(
      StateSnapshotController serverSnapshotController, LogStream serverLogstream) {
    this.replicatorSnapshotController = serverSnapshotController;
    this.serverLogstream = serverLogstream;
  }

  public void setFailSnapshotChunk(boolean fail) {
    this.failSnapshotChunk = fail;
  }

  @Override
  public CompletableFuture<SnapshotRestoreResponse> requestSnapshotChunk(
      MemberId server, SnapshotRestoreRequest request) {
    if (failSnapshotChunk) {
      return CompletableFuture.completedFuture(new InvalidSnapshotRestoreResponse());
    }
    return CompletableFuture.completedFuture(
        new DefaultSnapshotRequestHandler(replicatorSnapshotController)
            .onSnapshotRequest(request, NOPLogger.NOP_LOGGER));
  }

  @Override
  public CompletableFuture<LogReplicationResponse> requestLogReplication(
      MemberId server, LogReplicationRequest request) {
    return CompletableFuture.completedFuture(
        new DefaultLogReplicationRequestHandler(serverLogstream)
            .onReplicationRequest(request, NOPLogger.NOP_LOGGER));
  }

  @Override
  public CompletableFuture<RestoreInfoResponse> requestRestoreInfo(
      MemberId server, RestoreInfoRequest request) {
    if (autoResponse) {
      return CompletableFuture.completedFuture(
          new DefaultRestoreInfoRequestHandler(serverLogstream, replicatorSnapshotController)
              .onRestoreInfoRequest(request, NOPLogger.NOP_LOGGER));
    }
    return restoreInfoResponse;
  }

  public void completeRestoreInfoResponse(RestoreInfoResponse defaultRestoreInfoResponse) {
    autoResponse = false;
    restoreInfoResponse.complete(defaultRestoreInfoResponse);
  }

  public void completeRestoreInfoResponse(Throwable e) {
    autoResponse = false;
    restoreInfoResponse.completeExceptionally(e);
  }
}
