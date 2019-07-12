/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore;

import io.zeebe.distributedlog.restore.RestoreInfoResponse.ReplicationTarget;
import io.zeebe.distributedlog.restore.log.LogReplicationRequest;
import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreInfo;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreRequest;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreResponse;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public interface RestoreServer extends AutoCloseable {

  /**
   * Start serving snapshot requests to the given handler.
   *
   * @param handler to handle incoming requests
   * @return a future which will complete once the server is ready to accept requests
   */
  CompletableFuture<Void> serve(SnapshotRequestHandler handler);

  /**
   * Start serving {@link RestoreInfoRequest} requests to the given handler.
   *
   * @param server handler to handle incoming requests
   * @return a future which will complete once the server is ready to accept requests
   */
  CompletableFuture<Void> serve(RestoreInfoRequestHandler server);

  @Override
  void close();

  /**
   * Start serving {@link LogReplicationRequest} requests to the given handler.
   *
   * @param server handler to handle incoming requests
   * @return a future which will complete once the server is ready to accept requests
   */
  CompletableFuture<Void> serve(LogReplicationRequestHandler server);

  @FunctionalInterface
  interface SnapshotRequestHandler {

    /**
     * Handles a single {@link SnapshotRestoreRequest} request.
     *
     * @param request for a snapshot chunk
     * @param logger server logger
     */
    SnapshotRestoreResponse onSnapshotRequest(SnapshotRestoreRequest request, Logger logger);
  }

  @FunctionalInterface
  interface RestoreInfoRequestHandler {

    /**
     * Handles a single {@link RestoreInfoRequest} request. The returned response must be non-null.
     *
     * <p>If the returned {@link ReplicationTarget} is {@link ReplicationTarget#EVENTS}, then there
     * must exist an event locally at {@link RestoreInfoRequest#getLatestLocalPosition()}. If the
     * returned {@link ReplicationTarget} is {@link ReplicationTarget#SNAPSHOT}, then there must
     * exists a snapshot with a position greater than {@link
     * RestoreInfoRequest#getLatestLocalPosition()}. If {@link ReplicationTarget#SNAPSHOT}, response
     * must also include a non null {@link SnapshotRestoreInfo}
     *
     * @param request request to handle
     * @param logger server logger
     * @return response to return to sender
     */
    RestoreInfoResponse onRestoreInfoRequest(RestoreInfoRequest request, Logger logger);
  }

  @FunctionalInterface
  interface LogReplicationRequestHandler {

    /**
     * Each request will contain a range of event positions, and expect the returned events to have
     * positions strictly contained within that range. Furthermore, the first event serialized in
     * the response must be the next event right after the one at position equal to the request
     * {@link LogReplicationRequest#getFromPosition()}. If no event is found locally at that
     * position, the returned response must be invalid.
     *
     * <p>Additionally, events must be serialized in the order they were read on the local log, and
     * the last event position must be less than or equal to the {@link
     * LogReplicationRequest#getToPosition()}. If that position is less than the requested {@code
     * toPosition}, but there are more events locally with positions less than the {@code
     * toPosition}, then the response should indicate that and {@link
     * LogReplicationResponse#hasMoreAvailable()} should be true.
     *
     * @param request the request to server
     * @param logger server logger
     * @return response to return to the sender
     */
    LogReplicationResponse onReplicationRequest(LogReplicationRequest request, Logger logger);
  }
}
