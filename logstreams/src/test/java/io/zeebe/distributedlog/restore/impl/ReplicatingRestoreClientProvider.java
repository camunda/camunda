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
import io.zeebe.distributedlog.restore.RestoreFactory;
import io.zeebe.distributedlog.restore.RestoreNodeProvider;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreContext;
import org.slf4j.Logger;

public class ReplicatingRestoreClientProvider implements RestoreFactory {

  private final RestoreClient restoreClient;
  private final SnapshotRestoreContext snapshotRestoreContext;

  public ReplicatingRestoreClientProvider(
      RestoreClient restoreClient, SnapshotRestoreContext restoreContext) {
    this.restoreClient = restoreClient;
    this.snapshotRestoreContext = restoreContext;
  }

  @Override
  public RestoreNodeProvider createNodeProvider(int partitionId) {
    return () -> MemberId.from("1");
  }

  @Override
  public RestoreClient createClient(int partitionId) {
    return restoreClient;
  }

  @Override
  public SnapshotRestoreContext createSnapshotRestoreContext(int partitionId, Logger logger) {
    return this.snapshotRestoreContext;
  }
}
