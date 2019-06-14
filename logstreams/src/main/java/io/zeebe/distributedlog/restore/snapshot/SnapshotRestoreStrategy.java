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
package io.zeebe.distributedlog.restore.snapshot;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.RestoreStrategy;
import io.zeebe.distributedlog.restore.log.LogReplicator;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public class SnapshotRestoreStrategy implements RestoreStrategy {

  private final MemberId server;
  private final LogReplicator logReplicator;
  private final Logger log;
  private final long backupPosition;
  private final SnapshotRestoreInfo snapshotRestoreInfo;
  private final long latestLocalPosition;
  private RestoreSnapshotReplicator replicator;

  public SnapshotRestoreStrategy(
      LogReplicator logReplicator,
      RestoreSnapshotReplicator replicator,
      SnapshotRestoreInfo snapshotRestoreInfo,
      long latestLocalPosition,
      long backupPosition,
      MemberId server,
      Logger log) {
    this.logReplicator = logReplicator;
    this.replicator = replicator;
    this.snapshotRestoreInfo = snapshotRestoreInfo;
    this.latestLocalPosition = latestLocalPosition;
    this.backupPosition = backupPosition;
    this.server = server;
    this.log = log;
  }

  @Override
  public CompletableFuture<Long> executeRestoreStrategy() {
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
    log.debug("Restored snapshot. Restoring events from {} to {}", fromPosition, toPosition);
    return logReplicator.replicate(
        server, fromPosition, toPosition, fromPosition > latestLocalPosition);
  }

  private long getFirstEventToBeReplicated(long exporterPosition, long processedPosition) {
    return Math.min(processedPosition, exporterPosition);
  }
}
