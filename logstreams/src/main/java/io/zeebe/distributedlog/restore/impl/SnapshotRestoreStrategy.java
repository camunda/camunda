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
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.RestoreStrategy;
import io.zeebe.distributedlog.restore.log.LogReplicator;
import io.zeebe.logstreams.state.SnapshotRequester;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public class SnapshotRestoreStrategy implements RestoreStrategy {

  private final SnapshotRequester snapshotReplicator;
  private final MemberId server;
  private final RestoreClient client;
  private final LogReplicator logReplicator;
  private final Logger logger;
  private final long backupPosition;
  private final long latestLocalPosition;

  public SnapshotRestoreStrategy(
      RestoreClient client,
      LogReplicator logReplicator,
      SnapshotRequester snapshotReplicator,
      long latestLocalPosition,
      long backupPosition,
      MemberId server,
      Logger logger) {
    this.client = client;
    this.logReplicator = logReplicator;
    this.snapshotReplicator = snapshotReplicator;
    this.latestLocalPosition = latestLocalPosition;
    this.backupPosition = backupPosition;
    this.server = server;
    this.logger = logger;
  }

  @Override
  public CompletableFuture<Long> executeRestoreStrategy() {
    logger.debug("Restoring from snapshot");
    final CompletableFuture<Long> replicated = CompletableFuture.completedFuture(null);

    return replicated
        .thenCompose(nothing -> client.requestSnapshotInfo(server))
        .thenCompose(
            numSnapshots -> snapshotReplicator.getLatestSnapshotsFrom(server, numSnapshots > 1))
        .thenCompose(nothing -> onSnapshotsReplicated());
  }

  private CompletableFuture<Long> onSnapshotsReplicated() {
    final long exporterPosition = snapshotReplicator.getExporterPosition();
    final long processedPosition = snapshotReplicator.getProcessedPosition();
    final long fromPosition =
        Math.max(
            latestLocalPosition, // if exporter position is behind latestLocalPosition
            getFirstEventToBeReplicated(exporterPosition, processedPosition));
    final long toPosition = Math.max(processedPosition, backupPosition);
    // TODO: logstream.deleteAll(). https://github.com/zeebe-io/zeebe/issues/2509
    logger.debug("Restored snapshot. Restoring events from {} to {}", fromPosition, toPosition);
    return logReplicator.replicate(
        server, fromPosition, toPosition, fromPosition > latestLocalPosition);
  }

  private long getFirstEventToBeReplicated(long exporterPosition, long processedPosition) {
    if (exporterPosition > 0) {
      return Math.min(processedPosition, exporterPosition);
    }
    return processedPosition;
  }
}
