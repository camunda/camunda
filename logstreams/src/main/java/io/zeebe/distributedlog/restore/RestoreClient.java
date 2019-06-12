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
