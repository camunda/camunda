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
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreRequest;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ControllableRestoreClient implements RestoreClient {
  private final Map<Long, CompletableFuture<LogReplicationResponse>> logReplicationRequests =
      new HashMap<>();

  private final Map<Integer, CompletableFuture<SnapshotRestoreResponse>>
      snapshotReplicationRequests = new HashMap<>();
  public List<LogReplicationRequest> requestLog = new ArrayList<>();

  @Override
  public CompletableFuture<SnapshotRestoreResponse> requestSnapshotChunk(
      MemberId server, SnapshotRestoreRequest request) {
    return snapshotReplicationRequests.computeIfAbsent(
        request.getChunkIdx(), k -> new CompletableFuture<>());
  }

  @Override
  public CompletableFuture<LogReplicationResponse> requestLogReplication(
      MemberId server, LogReplicationRequest request) {
    final CompletableFuture<LogReplicationResponse> result = new CompletableFuture<>();
    logReplicationRequests.put(request.getFromPosition(), result);
    requestLog.add(request);
    return result;
  }

  @Override
  public CompletableFuture<RestoreInfoResponse> requestRestoreInfo(
      MemberId server, RestoreInfoRequest request) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  public void completeRequestSnapshotChunk(int chunkIdx, SnapshotRestoreResponse response) {
    snapshotReplicationRequests
        .computeIfAbsent(chunkIdx, k -> new CompletableFuture<>())
        .complete(response);
  }

  public void completeRequestSnapshotChunk(int chunkIdx, Throwable throwable) {
    snapshotReplicationRequests
        .computeIfAbsent(chunkIdx, k -> new CompletableFuture<>())
        .completeExceptionally(throwable);
  }

  public List<LogReplicationRequest> getRequestLog() {
    return requestLog;
  }

  public void completeLogReplication(long from, Throwable ex) {
    logReplicationRequests.get(from).completeExceptionally(ex);
  }

  public void completeLogReplication(long from, LogReplicationResponse response) {
    logReplicationRequests.get(from).complete(response);
  }

  public Map<Long, CompletableFuture<LogReplicationResponse>> getLogReplicationRequests() {
    return logReplicationRequests;
  }

  public void reset() {
    logReplicationRequests.clear();
    snapshotReplicationRequests.clear();
  }
}
