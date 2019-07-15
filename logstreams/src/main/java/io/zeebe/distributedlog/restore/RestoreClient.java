/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.log.LogReplicationRequest;
import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreRequest;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreResponse;
import java.util.concurrent.CompletableFuture;

public interface RestoreClient {
  /**
   * Requests a snapshot chunk from the server.
   *
   * @param server target cluster member
   */
  CompletableFuture<SnapshotRestoreResponse> requestSnapshotChunk(
      MemberId server, SnapshotRestoreRequest request);

  /**
   * Sends a log replication request to the given cluster member.
   *
   * @param server target cluster member
   * @param request request to send
   * @return the server response as a future
   */
  CompletableFuture<LogReplicationResponse> requestLogReplication(
      MemberId server, LogReplicationRequest request);

  /**
   * Requests what should be replicated from the given restore server.
   *
   * @param server the node to restore from
   * @param request the restore requirements
   * @return a future which completes with what information on what should be replicated from the
   *     server
   */
  CompletableFuture<RestoreInfoResponse> requestRestoreInfo(
      MemberId server, RestoreInfoRequest request);
}
