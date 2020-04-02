/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.raft.storage.snapshot.impl;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import io.atomix.raft.storage.snapshot.Snapshot;
import io.atomix.raft.storage.snapshot.SnapshotChunkReader;
import io.atomix.utils.time.WallClockTimestamp;
import java.util.Comparator;
import java.util.Objects;

/**
 * Manages reading and writing a single snapshot file.
 *
 * <p>User-provided state machines which implement the {@link Snapshottable} interface transparently
 * write snapshots to and read snapshots from files on disk. Each time a snapshot is taken of the
 * state machine state, the snapshot will be written to a single file represented by this interface.
 * Snapshots are backed by a {@link io.atomix.storage.buffer.Buffer} dictated by the parent {@link
 * io.atomix.storage.StorageLevel} configuration. Snapshots for file-based storage levels like
 * {@link io.atomix.storage.StorageLevel#DISK DISK} will be stored in a disk backed buffer, and
 * {@link io.atomix.storage.StorageLevel#MEMORY MEMORY} snapshots will be stored in an on-heap
 * buffer.
 *
 * <p>Snapshots are read and written by a {@link SnapshotReader} and {@link SnapshotWriter}
 * respectively. To create a reader or writer, use the {@link #openReader()} and {@link
 * #openWriter()} methods.
 *
 * <p>
 *
 * <pre>{@code
 * Snapshot snapshot = snapshotStore.snapshot(1);
 * try (SnapshotWriter writer = snapshot.writer()) {
 *   writer.writeString("Hello world!");
 * }
 * snapshot.complete();
 *
 * }</pre>
 *
 * A {@link SnapshotReader} is not allowed to be created until a {@link SnapshotWriter} has
 * completed writing the snapshot file and the snapshot has been marked {@link #complete()
 * complete}. This allows snapshots to effectively be written and closed but not completed until
 * other conditions are met. Prior to the completion of a snapshot, a failure and recovery of the
 * parent {@link DefaultSnapshotStore} will <em>not</em> recover an incomplete snapshot. Once a
 * snapshot is complete, the snapshot becomes immutable, can be recovered after a failure, and can
 * be read by multiple readers concurrently.
 */
public abstract class DefaultSnapshot implements Snapshot {

  protected final DefaultSnapshotDescriptor descriptor;
  protected final DefaultSnapshotStore store;
  private SnapshotWriter writer;

  protected DefaultSnapshot(
      final DefaultSnapshotDescriptor descriptor, final DefaultSnapshotStore store) {
    this.descriptor = checkNotNull(descriptor, "descriptor cannot be null");
    this.store = checkNotNull(store, "store cannot be null");
  }

  /**
   * Returns the snapshot timestamp.
   *
   * <p>The timestamp is the wall clock time at the {@link #index()} at which the snapshot was
   * taken.
   *
   * @return The snapshot timestamp.
   */
  @Override
  public WallClockTimestamp timestamp() {
    return WallClockTimestamp.from(descriptor.timestamp());
  }

  /**
   * Returns the snapshot format version.
   *
   * @return the snapshot format version
   */
  @Override
  public int version() {
    return descriptor.version();
  }

  /**
   * Returns the snapshot index.
   *
   * <p>The snapshot index is the index of the state machine at the point at which the snapshot was
   * written.
   *
   * @return The snapshot index.
   */
  @Override
  public long index() {
    return descriptor.index();
  }

  /**
   * Returns the snapshot term.
   *
   * <p>The snapshot term is the term of the state machine at the point at which the snapshot was
   * written.
   *
   * @return The snapshot term.
   */
  @Override
  public long term() {
    return descriptor.term();
  }

  @Override
  public SnapshotChunkReader newChunkReader() {
    return new DefaultSnapshotChunkReader(openReader());
  }

  /** Closes the snapshot. */
  @Override
  public void close() {}

  /** Deletes the snapshot. */
  @Override
  public void delete() {}

  @Override
  public int compareTo(final Snapshot other) {
    return Comparator.comparingLong(Snapshot::index)
        .thenComparingLong(Snapshot::term)
        .thenComparing(Snapshot::timestamp)
        .compare(this, other);
  }

  /**
   * Completes writing the snapshot to persist it and make it available for reads.
   *
   * <p>Snapshot writers must call this method to persist a snapshot to disk. Prior to completing a
   * snapshot, failure and recovery of the parent {@link DefaultSnapshotStore} will not result in
   * recovery of this snapshot. Additionally, no {@link #openReader() readers} can be created until
   * the snapshot has been completed.
   *
   * @return The completed snapshot.
   */
  @Override
  public Snapshot complete() {
    store.completeSnapshot(this);
    return this;
  }

  /** Closes the current snapshot reader. */
  @Override
  public void closeReader(final SnapshotReader reader) {}

  /** Closes the current snapshot writer. */
  @Override
  public void closeWriter(final SnapshotWriter writer) {
    this.writer = null;
  }

  /** Opens the given snapshot writer. */
  protected SnapshotWriter openWriter(
      final SnapshotWriter writer, final DefaultSnapshotDescriptor descriptor) {
    checkWriter();
    checkState(!descriptor.isLocked(), "cannot write to locked snapshot descriptor");
    this.writer = checkNotNull(writer, "writer cannot be null");
    return writer;
  }

  /** Checks that the snapshot can be written. */
  protected void checkWriter() {
    checkState(writer == null, "cannot create multiple writers for the same snapshot");
  }

  /** Opens the given snapshot reader. */
  protected SnapshotReader openReader(
      final SnapshotReader reader, final DefaultSnapshotDescriptor descriptor) {
    checkState(descriptor.isLocked(), "cannot read from unlocked snapshot descriptor");
    return reader;
  }

  @Override
  public int hashCode() {
    return Objects.hash(index());
  }

  @Override
  public boolean equals(final Object object) {
    if (object == null || getClass() != object.getClass()) {
      return false;
    }
    final Snapshot snapshot = (Snapshot) object;
    return snapshot.index() == index() && snapshot.term() == term();
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("index", index()).add("term", term()).toString();
  }
}
