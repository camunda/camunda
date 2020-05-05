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

import io.atomix.utils.time.WallClockTimestamp;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Manages reading and writing a single snapshot.
 *
 * <p>Snapshots are read using {@link SnapshotChunkReader}. To create a reader, use the {@link
 * #newChunkReader()}.
 *
 * <p>Snapshots are written using a {@link PendingSnapshot} implementation, which handles creating
 * temporary snapshots until they are committed.
 *
 * <pre>{@code
 * Snapshot snapshot = snapshotStore.snapshot(1);
 * try (SnapshotChunkReader reader = snapshot.newChunkReader()) {
 *   reader.seek(previousChunkId);
 *   if (reader.hasNext()) {
 *     final SnapshotChunk chunk = reader.next();
 *     // do something
 *   }
 * }
 * }</pre>
 */
public interface Snapshot extends AutoCloseable, Comparable<Snapshot> {

  /**
   * Returns the snapshot timestamp.
   *
   * <p>The timestamp is the wall clock time at the {@link #index()} at which the snapshot was
   * taken.
   *
   * @return The snapshot timestamp.
   */
  WallClockTimestamp timestamp();

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
   * written.
   *
   * @return The snapshot index.
   */
  long index();

  /**
   * Returns the snapshot term.
   *
   * <p>The snapshot term is the term of the state machine at the point at which the snapshot was
   * written.
   *
   * @return The snapshot term.
   */
  long term();

  /**
   * Returns a new snapshot chunk reader for this snapshot. Chunk readers are meant to be one-time
   * use and as such don't have to be thread-safe.
   *
   * @return a new snapshot chunk reader
   */
  SnapshotChunkReader newChunkReader();

  /** Closes the snapshot. */
  @Override
  void close();

  /** Deletes the snapshot. */
  void delete();

  /** @return a path to the snapshot location */
  Path getPath();

  @Override
  default int compareTo(final Snapshot other) {
    return Comparator.comparingLong(Snapshot::index)
        .thenComparingLong(Snapshot::term)
        .thenComparing(Snapshot::timestamp)
        .compare(this, other);
  }
}
