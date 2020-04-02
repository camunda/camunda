/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.storage.snapshot;

import java.nio.file.Path;

/**
 * Creates a snapshot store which should store its {@link Snapshot} and {@link PendingSnapshot}
 * instances in the given directory.
 */
@FunctionalInterface
public interface SnapshotStoreFactory {

  /**
   * Creates a snapshot store operating in the given {@code directory}.
   *
   * @param directory the root directory where snapshots should be stored
   * @param partitionName the partition name for this store
   * @return a new {@link SnapshotStore}
   */
  SnapshotStore createSnapshotStore(Path directory, String partitionName);
}
