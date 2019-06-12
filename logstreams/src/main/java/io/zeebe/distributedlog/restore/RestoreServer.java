/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.distributedlog.restore;

import io.zeebe.distributedlog.restore.RestoreInfoResponse.ReplicationTarget;
import io.zeebe.distributedlog.restore.log.LogReplicationRequest;
import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreInfo;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreRequest;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreResponse;
import java.util.concurrent.CompletableFuture;

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
     */
    SnapshotRestoreResponse onSnapshotRequest(SnapshotRestoreRequest request);
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
     * @return response to return to sender
     */
    RestoreInfoResponse onRestoreInfoRequest(RestoreInfoRequest request);
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
     * @return response to return to the sender
     */
    LogReplicationResponse onReplicationRequest(LogReplicationRequest request);
  }
}
