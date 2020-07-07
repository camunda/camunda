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
import java.nio.file.Path;

/** Represents a snapshot, which was persisted at the {@link PersistedSnapshotStore}. */
public interface PersistedSnapshot extends CloseableSilently {

  /**
   * Returns the snapshot timestamp.
   *
   * <p>The timestamp is the wall clock time at the {@link #getIndex()} at which the snapshot was
   * taken.
   *
   * @return The snapshot timestamp.
   */
  WallClockTimestamp getTimestamp();

  /**
   * Returns the snapshot format version.
   *
   * @return the snapshot format version
   */
  int version();

  /**
   * Returns the snapshot index.
   *
   * <p>The snapshot index is the index of the state machine at the point at which the snapshot was
   * persisted.
   *
   * @return The snapshot index.
   */
  long getIndex();

  /**
   * Returns the snapshot term.
   *
   * <p>The snapshot term is the term of the state machine at the point at which the snapshot was
   * persisted.
   *
   * @return The snapshot term.
   */
  long getTerm();

  /**
   * Returns a new snapshot chunk reader for this snapshot. Chunk readers are meant to be one-time
   * use and as such don't have to be thread-safe.
   *
   * @return a new snapshot chunk reader
   */
  SnapshotChunkReader newChunkReader();

  /** Deletes the snapshot. */
  void delete();

  /** @return a path to the snapshot location */
  Path getPath();

  /**
   * Returns an implementation specific compaction bound, e.g. a log stream position, a timestamp,
   * etc., used during compaction
   *
   * @return the compaction upper bound
   */
  long getCompactionBound();

  /** @return the identifier of the snapshot */
  SnapshotId getId();
}
