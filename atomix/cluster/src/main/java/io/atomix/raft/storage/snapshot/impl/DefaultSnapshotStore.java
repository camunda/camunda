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

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.raft.storage.snapshot.PendingSnapshot;
import io.atomix.raft.storage.snapshot.Snapshot;
import io.atomix.raft.storage.snapshot.SnapshotListener;
import io.atomix.raft.storage.snapshot.SnapshotStore;
import io.atomix.storage.buffer.FileBuffer;
import io.atomix.utils.time.WallClockTimestamp;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSnapshotStore implements SnapshotStore {

  private final File directory;
  private final String partitionName;
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final NavigableMap<Long, Snapshot> snapshots = new ConcurrentSkipListMap<>();

  public DefaultSnapshotStore(final Path directory, final String partitionName) {
    this.directory = checkNotNull(directory, "directory cannot be null").toFile();
    this.partitionName = partitionName;
    open();
  }

  /** Opens the snapshot manager. */
  private void open() {
    for (final Snapshot snapshot : loadSnapshots()) {
      completeSnapshot(snapshot);
    }
  }

  /**
   * Loads all available snapshots from disk.
   *
   * @return A list of available snapshots.
   */
  private Collection<Snapshot> loadSnapshots() {
    // Ensure log directories are created.
    directory.mkdirs();

    final File[] files = directory.listFiles(File::isFile);
    if (files == null) {
      return Collections.emptyList();
    }

    // Iterate through all files in the log directory.
    final List<Snapshot> snapshots = new ArrayList<>();
    for (final File file : files) {

      // If the file looks like a segment file, attempt to load the segment.
      if (DefaultSnapshotFile.isSnapshotFile(file)) {
        final DefaultSnapshotFile defaultSnapshotFile = new DefaultSnapshotFile(file, null);
        final DefaultSnapshotDescriptor descriptor =
            new DefaultSnapshotDescriptor(
                FileBuffer.allocate(file, DefaultSnapshotDescriptor.BYTES));

        // Valid segments will have been locked. Segments that resulting from failures during log
        // cleaning will be
        // unlocked and should ultimately be deleted from disk.
        if (descriptor.isLocked()) {
          log.debug(
              "Loaded disk snapshot: {} ({})",
              descriptor.index(),
              defaultSnapshotFile.file().getName());
          snapshots.add(new FileSnapshot(defaultSnapshotFile, descriptor, this));
          descriptor.close();
        }
      }
    }

    return snapshots;
  }

  /** Completes writing a snapshot. */
  protected synchronized void completeSnapshot(final Snapshot snapshot) {
    checkNotNull(snapshot, "snapshot cannot be null");

    final Map.Entry<Long, Snapshot> lastEntry = snapshots.lastEntry();
    if (lastEntry == null) {
      snapshots.put(snapshot.index(), snapshot);
    } else if (lastEntry.getValue().index() == snapshot.index()) {
      // in case of concurrently trying to complete the same snapshot
      snapshot.close();
    } else if (lastEntry.getValue().index() < snapshot.index()) {
      snapshots.put(snapshot.index(), snapshot);
      final Snapshot lastSnapshot = lastEntry.getValue();
      lastSnapshot.close();
      lastSnapshot.delete();
    } else {
      snapshot.close();
      snapshot.delete();
    }
  }

  /**
   * Returns the snapshot at the given index.
   *
   * @param index the index for which to lookup the snapshot
   * @return the snapshot at the given index or {@code null} if the snapshot doesn't exist
   */
  @Override
  public Snapshot getSnapshot(final long index) {
    return snapshots.get(index);
  }

  @Override
  public void close() {}

  /**
   * Returns the index of the current snapshot. Defaults to 0.
   *
   * @return the index of the current snapshot
   */
  @Override
  public long getCurrentSnapshotIndex() {
    final Snapshot snapshot = getCurrentSnapshot();
    return snapshot != null ? snapshot.index() : 0L;
  }

  /**
   * Returns the current snapshot.
   *
   * @return the current snapshot
   */
  @Override
  public Snapshot getCurrentSnapshot() {
    final Map.Entry<Long, Snapshot> entry = snapshots.lastEntry();
    return entry != null ? entry.getValue() : null;
  }

  /**
   * Deletes a {@link SnapshotStore} from disk.
   *
   * <p>The snapshot store will be deleted by simply reading {@code snapshot} file names from disk
   * and deleting snapshot files directly. Deleting the snapshot store does not involve reading any
   * snapshot files into memory.
   */
  @Override
  public void delete() {
    final File[] files =
        directory.listFiles(f -> f.isFile() && DefaultSnapshotFile.isSnapshotFile(f));
    if (files == null) {
      return;
    }

    // Iterate through all files in the storage directory.
    for (final File file : files) {
      try {
        Files.delete(file.toPath());
      } catch (final IOException e) {
        // Ignore the exception.
      }
    }
  }

  @Override
  public PendingSnapshot newPendingSnapshot(
      final long index, final long term, final WallClockTimestamp timestamp) {
    return new DefaultPendingSnapshot(newSnapshot(index, term, timestamp));
  }

  /**
   * Creates a new snapshot.
   *
   * @param index The snapshot index.
   * @param timestamp The snapshot timestamp.
   * @return The snapshot.
   */
  @Override
  public Snapshot newSnapshot(
      final long index, final long term, final WallClockTimestamp timestamp) {
    final DefaultSnapshotDescriptor descriptor =
        DefaultSnapshotDescriptor.builder()
            .withIndex(index)
            .withTerm(term)
            .withTimestamp(timestamp.unixTimestamp())
            .build();

    return createDiskSnapshot(descriptor);
  }

  @Override
  public void purgeSnapshots(final Snapshot snapshot) {
    throw new UnsupportedOperationException(
        "This operation is not supported in the old implementation");
  }

  @Override
  public void purgePendingSnapshots() {
    // nothing to do, as snapshots are temporary files
  }

  @Override
  public Path getPath() {
    return directory.toPath();
  }

  @Override
  public Collection<? extends Snapshot> getSnapshots() {
    return snapshots.values();
  }

  @Override
  public void addListener(final SnapshotListener listener) {
    throw new UnsupportedOperationException(
        "This operation is not supported on the old implementation");
  }

  @Override
  public void removeListener(final SnapshotListener listener) {
    throw new UnsupportedOperationException(
        "This operation is not supported on the old implementation");
  }

  /** Creates a disk snapshot. */
  private Snapshot createDiskSnapshot(final DefaultSnapshotDescriptor descriptor) {
    final File snapshotFile =
        DefaultSnapshotFile.createSnapshotFile(directory, partitionName, descriptor.index());
    final File temporaryFile = DefaultSnapshotFile.createTemporaryFile(snapshotFile);

    final DefaultSnapshotFile file = new DefaultSnapshotFile(snapshotFile, temporaryFile);
    final Snapshot snapshot = new FileSnapshot(file, descriptor, this);
    log.debug("Created disk snapshot: {}", snapshot);
    return snapshot;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
