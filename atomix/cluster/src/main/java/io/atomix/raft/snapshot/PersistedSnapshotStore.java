/*
 * Copyright © 2020  camunda services GmbH (info@camunda.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.atomix.raft.snapshot;

import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.util.CloseableSilently;
import java.io.IOException;
import java.util.Optional;

/**
 * Represents a store, which allows to persist snapshots on a storage, which is implementation
 * dependent. It is possible to take a transient snapshot, which means you can start taking an
 * snapshot and can persist it later or abort it. Furthermore it is possible to persist/receive
 * {@link SnapshotChunk}'s from an already {@link PersistedSnapshot} and persist them in this
 * current store.
 *
 * <p>Only one {@link PersistedSnapshot} at a time is stored in the {@link PersistedSnapshotStore}
 * and can be received via {@link PersistedSnapshotStore#getLatestSnapshot()}.
 */
public interface PersistedSnapshotStore extends CloseableSilently {

  /**
   * Returns true if the given identifier is equal to the snapshot id of the current persisted
   * snapshot, false otherwise.
   *
   * @param id the snapshot Id to look for
   * @return true if the current snapshot has the equal Id, false otherwise
   * @see SnapshotId#getSnapshotIdAsString()
   */
  boolean hasSnapshotId(String id);

  /**
   * Starts a new transient snapshot which can be persisted after the snapshot was taken.
   *
   * @param index the index to which the snapshot corresponds to
   * @param term the term to which the snapshots corresponds to
   * @param timestamp the time to which the snapshots corresponds to
   * @return the new transient snapshot
   */
  TransientSnapshot newTransientSnapshot(
      final long index, final long term, final WallClockTimestamp timestamp);

  /**
   * Starts a new received volatile snapshot which can be persisted later.
   *
   * @param snapshotId the snapshot id which is defined as {@code index-term-timestamp}
   * @return the new volatile received snapshot
   */
  ReceivedSnapshot newReceivedSnapshot(String snapshotId);

  /** @return the latest {@link PersistedSnapshot} if exists */
  Optional<PersistedSnapshot> getLatestSnapshot();

  /**
   * Purges all ongoing pending/transient/volatile snapshots.
   *
   * @throws IOException when there was an unexpected IO issue
   */
  void purgePendingSnapshots() throws IOException;

  /**
   * Adds an {@link PersistedSnapshotListener} to the store, which is notified when a new {@link
   * PersistedSnapshot} is persisted at this store.
   *
   * @param listener the listener which should be added and notified later
   */
  void addSnapshotListener(PersistedSnapshotListener listener);

  /**
   * Removes an registered {@link PersistedSnapshotListener} from the store. The listener will no
   * longer called when a new {@link PersistedSnapshot} is persisted at this store.
   *
   * @param listener the listener which should be removed
   */
  void removeSnapshotListener(PersistedSnapshotListener listener);

  /**
   * @return the snapshot index of the latest {@link PersistedSnapshot}
   * @see PersistedSnapshotStore#getLatestSnapshot()
   */
  long getCurrentSnapshotIndex();

  /**
   * Deletes a {@link PersistedSnapshotStore} from disk.
   *
   * <p>The snapshot store will be deleted by simply reading {@code snapshot} file names from disk
   * and deleting snapshot files directly. Deleting the snapshot store does not involve reading any
   * snapshot files into memory.
   */
  void delete();
}
