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
package io.zeebe.distributedlog.restore.impl;

import io.atomix.cluster.MemberId;
import io.atomix.utils.concurrent.Scheduler;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.RestoreInfoRequest;
import io.zeebe.distributedlog.restore.RestoreInfoResponse;
import io.zeebe.distributedlog.restore.RestoreNodeProvider;
import io.zeebe.distributedlog.restore.RestoreStrategy;
import io.zeebe.distributedlog.restore.RestoreStrategyPicker;
import io.zeebe.distributedlog.restore.log.LogReplicator;
import io.zeebe.logstreams.state.SnapshotRequester;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

/**
 * Implements a restore picking which uses the current stream processor leader as the restore
 * server. If the local node is the current stream processor leader, then it will withdraw from the
 * election, and retry. If there is no leader, then it will simply retry forever with a slight
 * delay.
 */
public class DefaultStrategyPicker implements RestoreStrategyPicker {
  private final RestoreClient client;
  private final SnapshotRequester snapshotReplicator;
  private final Scheduler scheduler;
  private final LogReplicator logReplicator;
  private final RestoreNodeProvider nodeProvider;
  private final Logger logger;

  public DefaultStrategyPicker(
      RestoreClient client,
      RestoreNodeProvider nodeProvider,
      LogReplicator logReplicator,
      SnapshotRequester snapshotReplicator,
      Scheduler scheduler,
      Logger logger) {
    this.client = client;
    this.nodeProvider = nodeProvider;
    this.logReplicator = logReplicator;
    this.snapshotReplicator = snapshotReplicator;
    this.scheduler = scheduler;
    this.logger = logger;
  }

  @Override
  public CompletableFuture<RestoreStrategy> pick(long latestLocalPosition, long backupPosition) {
    return findRestoreServer()
        .thenCompose(
            server -> this.onRestoreServerFound(server, latestLocalPosition, backupPosition));
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
      scheduler.schedule(Duration.ofMillis(100), () -> tryFindRestoreServer(result));
    }
  }

  private CompletableFuture<RestoreStrategy> onRestoreServerFound(
      MemberId server, long latestLocalPosition, long backupPosition) {
    final RestoreInfoRequest request =
        new DefaultRestoreInfoRequest(latestLocalPosition, backupPosition);
    return client
        .requestRestoreInfo(server, request)
        .thenCompose(
            response ->
                onRestoreInfoReceived(server, latestLocalPosition, backupPosition, response));
  }

  private CompletableFuture<RestoreStrategy> onRestoreInfoReceived(
      MemberId server,
      long latestLocalPosition,
      long backupPosition,
      RestoreInfoResponse response) {
    final CompletableFuture<RestoreStrategy> result = new CompletableFuture<>();

    switch (response.getReplicationTarget()) {
      case SNAPSHOT:
        final SnapshotRestoreStrategy snapshotRestoreStrategy =
            new SnapshotRestoreStrategy(
                client,
                logReplicator,
                snapshotReplicator,
                latestLocalPosition,
                backupPosition,
                server,
                logger);
        result.complete(snapshotRestoreStrategy);
        break;
      case EVENTS:
        result.complete(() -> logReplicator.replicate(server, latestLocalPosition, backupPosition));
        break;
      case NONE:
        result.completeExceptionally(new UnsupportedOperationException("Not yet implemented"));
        break;
    }

    return result;
  }
}
