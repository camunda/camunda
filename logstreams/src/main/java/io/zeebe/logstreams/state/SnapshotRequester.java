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
import java.util.concurrent.CompletableFuture;
import org.slf4j.LoggerFactory;

public class SnapshotRequester {
  private final RestoreClient client;
  private final ReplicationController processorSnapshotController;
  private final SnapshotRestoreContext restoreContext;
  private final int partitionId;
  private final StateStorage processorStorage;
  private final StateStorage exporterStorage;
  private final SnapshotReplication processorSnapshotReplicationConsumer;
  private final ReplicationController exporterSnapshotController;
  private final SnapshotReplication exporterSnapshotReplicationConsumer;

  public SnapshotRequester(
      RestoreClient client, SnapshotRestoreContext restoreContext, int partitionId) {
    this.client = client;
    this.restoreContext = restoreContext;
    this.partitionId = partitionId;

    exporterSnapshotReplicationConsumer =
        restoreContext.createExporterSnapshotReplicationConsumer(partitionId);
    processorSnapshotReplicationConsumer =
        restoreContext.createProcessorSnapshotReplicationConsumer(partitionId);
    processorStorage = restoreContext.getProcessorStateStorage(partitionId);
    exporterStorage = restoreContext.getExporterStateStorage(partitionId);

    this.processorSnapshotController =
        new ReplicationController(
            processorSnapshotReplicationConsumer, this.processorStorage, () -> {}, () -> -1L);

    this.exporterSnapshotController =
        new ReplicationController(
            exporterSnapshotReplicationConsumer, this.exporterStorage, () -> {}, () -> -1L);
  }

  public CompletableFuture<Long> getLatestSnapshotsFrom(
      MemberId server, boolean getExporterSnapshot) {

    processorSnapshotController.consumeReplicatedSnapshots(pos -> {});

    CompletableFuture<Long> replicated = CompletableFuture.completedFuture(null);

    if (getExporterSnapshot) {
      exporterSnapshotController.consumeReplicatedSnapshots(pos -> {});
      final CompletableFuture<Long> exporterFuture = new CompletableFuture<>();
      exporterSnapshotController.addListener(
          new DefaultSnapshotReplicationListener(
              exporterSnapshotController, exporterSnapshotReplicationConsumer, exporterFuture));
      replicated = replicated.thenCompose((nothing) -> exporterFuture);
    }

    final CompletableFuture<Long> future = new CompletableFuture<>();
    processorSnapshotController.addListener(
        new DefaultSnapshotReplicationListener(
            processorSnapshotController, processorSnapshotReplicationConsumer, future));
    replicated = replicated.thenCompose((nothing) -> future);

    client.requestLatestSnapshot(server);
    return replicated;
  }

  public long getExporterPosition() {
    return restoreContext.getExporterPositionSupplier(exporterStorage).get();
  }

  public long getProcessedPosition() {
    return restoreContext.getProcessorPositionSupplier(partitionId, processorStorage).get();
  }

  static class DefaultSnapshotReplicationListener implements SnapshotReplicationListener {
    private final ReplicationController controller;
    private SnapshotReplication consumer;
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
