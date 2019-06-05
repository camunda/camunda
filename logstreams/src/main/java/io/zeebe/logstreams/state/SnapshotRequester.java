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
package io.zeebe.logstreams.state;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreContext;
import io.zeebe.logstreams.impl.delete.NoopDeletionService;
import java.util.concurrent.CompletableFuture;
import org.slf4j.LoggerFactory;

public class SnapshotRequester {
  private final RestoreClient client;
  private final ReplicationController replicationController;
  private final SnapshotRestoreContext restoreContext;
  private final int partitionId;
  private final StateStorage stateStorage;
  private final SnapshotReplication snapshotReplicationConsumer;
  private final NoopDeletionService noopDeletion = new NoopDeletionService();

  public SnapshotRequester(
      RestoreClient client, SnapshotRestoreContext restoreContext, int partitionId) {
    this.client = client;
    this.restoreContext = restoreContext;
    this.partitionId = partitionId;

    snapshotReplicationConsumer = restoreContext.createSnapshotReplicationConsumer(partitionId);
    stateStorage = restoreContext.getStateStorage(partitionId);

    this.replicationController =
        new ReplicationController(snapshotReplicationConsumer, this.stateStorage);
  }

  public CompletableFuture<Long> getLatestSnapshotsFrom(
      MemberId server, boolean getExporterSnapshot) {

    replicationController.consumeReplicatedSnapshots();

    CompletableFuture<Long> replicated = CompletableFuture.completedFuture(null);

    if (getExporterSnapshot) {
      replicationController.consumeReplicatedSnapshots();
      final CompletableFuture<Long> exporterFuture = new CompletableFuture<>();
      replicationController.addListener(
          new DefaultSnapshotReplicationListener(
              replicationController, snapshotReplicationConsumer, exporterFuture));
      replicated = replicated.thenCompose((nothing) -> exporterFuture);
    }

    final CompletableFuture<Long> future = new CompletableFuture<>();
    replicationController.addListener(
        new DefaultSnapshotReplicationListener(
            replicationController, snapshotReplicationConsumer, future));
    replicated = replicated.thenCompose((nothing) -> future);

    client.requestLatestSnapshot(server);
    return replicated;
  }

  public long getExporterPosition() {
    return restoreContext.getExporterPositionSupplier(stateStorage).get();
  }

  public long getProcessedPosition() {
    return restoreContext.getProcessorPositionSupplier(partitionId, stateStorage).get();
  }

  static class DefaultSnapshotReplicationListener implements SnapshotReplicationListener {
    private final ReplicationController controller;
    private final SnapshotReplication consumer;
    private final CompletableFuture<Long> future;

    DefaultSnapshotReplicationListener(
        ReplicationController controller,
        SnapshotReplication consumer,
        CompletableFuture<Long> future) {
      this.controller = controller;
      this.consumer = consumer;
      this.future = future;
    }

    @Override
    public void onReplicated(long snapshotPosition) {
      LoggerFactory.getLogger("Restore").info("Replicated snapshot {}", snapshotPosition);
      try {
        future.complete(snapshotPosition);
      } catch (Exception e) {
        future.completeExceptionally(e);
      }
      consumer.close();
      controller.removeListener(this);
    }

    @Override
    public void onFailure(long snapshotPosition) {
      future.completeExceptionally(new FailedSnapshotReplication(snapshotPosition));
      controller.clearInvalidatedSnapshot(snapshotPosition);
      controller.removeListener(this);
    }
  }
}
