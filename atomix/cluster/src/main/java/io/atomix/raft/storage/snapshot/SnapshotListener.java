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

/** Represents a lifecycle listener for a {@link SnapshotStore}'s {@link Snapshot} collection. */
public interface SnapshotListener {

  /**
   * Called whenever a new snapshot is committed to the snapshot store.
   *
   * @param snapshot the newly committed snapshot
   * @param store the snapshot store to which it was added
   */
  void onNewSnapshot(Snapshot snapshot, SnapshotStore store);

  /**
   * Called whenever a committed snapshot has been deleted.
   *
   * @param snapshot the snapshot that was deleted
   * @param store the store from which it was removed
   */
  void onSnapshotDeletion(Snapshot snapshot, SnapshotStore store);
}
