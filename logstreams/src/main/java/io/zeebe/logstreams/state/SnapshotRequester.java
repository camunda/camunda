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
import io.zeebe.logstreams.spi.SnapshotController;
import java.util.concurrent.CompletableFuture;

public class SnapshotRequester implements SnapshotReplicationListener {

  private final OnDemandSnapshotReplication client;
  private CompletableFuture<Long> replicationFuture;
  private final SnapshotController snapshotController;

  public SnapshotRequester(
      OnDemandSnapshotReplication client, SnapshotController snapshotController) {
    this.client = client;
    this.snapshotController = snapshotController;
  }

  public CompletableFuture<Long> getLatestSnapshotFrom(MemberId server) {
    replicationFuture = new CompletableFuture<>();
    snapshotController.addListener(this);
    client.request(server);
    return replicationFuture;
  }

  @Override
  public void onReplicated(long snapshotPosition) {
    replicationFuture.complete(snapshotPosition);
    snapshotController.removeListener(this);
  }

  @Override
  public void onFailure(long snapshotPosition) {
    replicationFuture.completeExceptionally(new FailedSnapshotReplication(snapshotPosition));
    snapshotController.enableRetrySnapshot(snapshotPosition);
    snapshotController.removeListener(this);
  }
}
