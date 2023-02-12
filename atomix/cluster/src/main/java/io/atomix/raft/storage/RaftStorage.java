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
 * limitations under the License.
 */
package io.atomix.raft.storage;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogFlusher;
import io.atomix.raft.storage.system.MetaStore;
import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Immutable log configuration and {@link RaftLog} factory.
 *
 * <p>This class provides a factory for {@link RaftLog} objects. {@code Storage} objects are
 * immutable and can be created only via the {@link RaftStorage.Builder}. To create a new {@code
 * Storage.Builder}, use the static {@link #builder()} factory method:
 *
 * <pre>{@code
 * Storage storage = Storage.builder()
 *   .withDirectory(new File("logs"))
 *   .withStorageLevel(StorageLevel.DISK)
 *   .build();
 *
 * }</pre>
 *
 * @see RaftLog
 */
public final class RaftStorage {

  private final String prefix;
  private final int partitionId;
  private final File directory;
  private final int maxSegmentSize;
  private final long freeDiskSpace;
  private final ReceivableSnapshotStore persistedSnapshotStore;
  private final int journalIndexDensity;
  private final boolean preallocateSegmentFiles;
  private final RaftLogFlusher.Factory flusherFactory;

  private RaftStorage(
      final String prefix,
      final int partitionId,
      final File directory,
      final int maxSegmentSize,
      final long freeDiskSpace,
      final RaftLogFlusher.Factory flusherFactory,
      final ReceivableSnapshotStore persistedSnapshotStore,
      final int journalIndexDensity,
      final boolean preallocateSegmentFiles) {
    this.prefix = prefix;
    this.partitionId = partitionId;
    this.directory = directory;
    this.maxSegmentSize = maxSegmentSize;
    this.freeDiskSpace = freeDiskSpace;
    this.flusherFactory = flusherFactory;
    this.persistedSnapshotStore = persistedSnapshotStore;
    this.journalIndexDensity = journalIndexDensity;
    this.preallocateSegmentFiles = preallocateSegmentFiles;

    try {
      FileUtil.ensureDirectoryExists(directory.toPath());
    } catch (final IOException e) {
      throw new UncheckedIOException(
          String.format("Failed to create partition's directory %s", directory.toPath()), e);
    }
  }

  /**
   * Returns a new storage builder.
   *
   * @return A new storage builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the storage filename prefix.
   *
   * @return The storage filename prefix.
   */
  public String prefix() {
    return prefix;
  }

  /**
   * Attempts to acquire a lock on the storage directory.
   *
   * @param id the ID with which to lock the directory
   * @return indicates whether the lock was successfully acquired
   */
  public boolean lock(final String id) {
    final File lockFile = new File(directory, String.format(".%s.lock", prefix));
    final File tempLockFile = new File(directory, String.format(".%s.lock.tmp", id));
    try {
      if (!lockFile.exists()) {
        // Create and update the file atomically
        Files.writeString(
            tempLockFile.toPath(),
            id,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
            StandardOpenOption.SYNC);

        // If two nodes tries to acquire lock, move will fail with FileAlreadyExistsException
        FileUtil.moveDurably(
            tempLockFile.toPath(), lockFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
      }
      // Read the lock file again to ensure that contents matches the local id
      final String lock = Files.readString(lockFile.toPath());
      return lock != null && lock.equals(id);
    } catch (final FileAlreadyExistsException e) {
      return false;
    } catch (final IOException e) {
      throw new StorageException("Failed to acquire storage lock", e);
    }
  }

  /**
   * Opens a new {@link MetaStore}, recovering metadata from disk if it exists.
   *
   * <p>The meta store will be loaded from disk, or if missing, a new meta store will be created.
   *
   * @return The metastore.
   */
  public MetaStore openMetaStore() {
    try {
      return new MetaStore(this);
    } catch (final IOException e) {
      throw new StorageException("Failed to open metastore", e);
    }
  }

  /**
   * Returns the {@link PersistedSnapshotStore}.
   *
   * @return The snapshot store.
   */
  public ReceivableSnapshotStore getPersistedSnapshotStore() {
    return persistedSnapshotStore;
  }

  /**
   * Opens a new {@link RaftLog}, recovering the log from disk if it exists.
   *
   * <p>When a log is opened, the log will attempt to load segments from the storage {@link
   * #directory()} according to the provided log {@code name}. If segments for the given log name
   * are present on disk, segments will be loaded and indexes will be rebuilt from disk. If no
   * segments are found, an empty log will be created.
   *
   * <p>When log files are loaded from disk, the file names are expected to be based on the provided
   * log {@code name}.
   *
   * @return The opened log.
   */
  public RaftLog openLog(final MetaStore metaStore, final ThreadContext flushContext) {

    return RaftLog.builder()
        .withName(prefix)
        .withPartitionId(partitionId)
        .withDirectory(directory)
        .withMaxSegmentSize(maxSegmentSize)
        .withFreeDiskSpace(freeDiskSpace)
        .withJournalIndexDensity(journalIndexDensity)
        .withLastFlushedIndex(metaStore.lastFlushedIndex())
        .withPreallocateSegmentFiles(preallocateSegmentFiles)
        .withFlushMetaStore(metaStore::storeLastFlushedIndex)
        .withFlusher(flusherFactory.createFlusher(flushContext))
        .build();
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("directory", directory()).toString();
  }

  /**
   * Returns the storage directory.
   *
   * <p>The storage directory is the directory to which all {@link RaftLog}s write files. Segment
   * files for multiple logs may be stored in the storage directory, and files for each log instance
   * will be identified by the {@code name} provided when the log is {@link #openLog(MetaStore,
   * ThreadContext) opened}.
   *
   * @return The storage directory.
   */
  public File directory() {
    return directory;
  }

  /** The ID of the partition associated with this storage. */
  public int partitionId() {
    return partitionId;
  }

  /**
   * Builds a {@link RaftStorage} configuration.
   *
   * <p>The storage builder provides simplifies building more complex {@link RaftStorage}
   * configurations. To create a storage builder, use the {@link #builder()} factory method. Set
   * properties of the configured {@code Storage} object with the various {@code with*} methods.
   * Once the storage has been configured, call {@link #build()} to build the object.
   *
   * <pre>{@code
   * Storage storage = Storage.builder()
   *   .withDirectory(new File("logs"))
   *   .withPersistenceLevel(PersistenceLevel.DISK)
   *   .build();
   *
   * }</pre>
   */
  public static final class Builder implements io.atomix.utils.Builder<RaftStorage> {

    private static final String DEFAULT_PREFIX = "atomix";
    private static final String DEFAULT_DIRECTORY =
        System.getProperty("atomix.data", System.getProperty("user.dir"));
    private static final int DEFAULT_MAX_SEGMENT_SIZE = 1024 * 1024 * 32;
    private static final long DEFAULT_FREE_DISK_SPACE = 1024L * 1024 * 1024;
    private static final RaftLogFlusher.Factory DEFAULT_FLUSHER_FACTORY =
        RaftLogFlusher.Factory::direct;
    private static final int DEFAULT_JOURNAL_INDEX_DENSITY = 100;
    private static final boolean DEFAULT_PREALLOCATE_SEGMENT_FILES = true;

    // impossible value to make it clear it's unset and there's an error
    private static final int DEFAULT_PARTITION_ID = -1;

    private String prefix = DEFAULT_PREFIX;
    private File directory = new File(DEFAULT_DIRECTORY);
    private int maxSegmentSize = DEFAULT_MAX_SEGMENT_SIZE;
    private long freeDiskSpace = DEFAULT_FREE_DISK_SPACE;
    private RaftLogFlusher.Factory flusherFactory = DEFAULT_FLUSHER_FACTORY;
    private ReceivableSnapshotStore persistedSnapshotStore;
    private int journalIndexDensity = DEFAULT_JOURNAL_INDEX_DENSITY;
    private boolean preallocateSegmentFiles = DEFAULT_PREALLOCATE_SEGMENT_FILES;
    private int partitionId = DEFAULT_PARTITION_ID;

    private Builder() {}

    /**
     * Sets the storage prefix.
     *
     * @param prefix The storage prefix.
     * @return The storage builder.
     */
    public Builder withPrefix(final String prefix) {
      this.prefix = checkNotNull(prefix, "prefix cannot be null");
      return this;
    }

    /**
     * Sets the log directory, returning the builder for method chaining.
     *
     * <p>The log will write segment files into the provided directory. If multiple {@link
     * RaftStorage} objects are located on the same machine, they write logs to different
     * directories.
     *
     * @param directory The log directory.
     * @return The storage builder.
     * @throws NullPointerException If the {@code directory} is {@code null}
     */
    public Builder withDirectory(final File directory) {
      this.directory = checkNotNull(directory, "directory");
      return this;
    }

    /**
     * Sets the maximum segment size in bytes, returning the builder for method chaining.
     *
     * <p>The maximum segment size dictates when logs should roll over to new segments. As entries
     * are written to a segment of the log, once the size of the segment surpasses the configured
     * maximum segment size, the log will create a new segment and append new entries to that
     * segment.
     *
     * <p>By default, the maximum segment size is {@code 1024 * 1024 * 32}.
     *
     * @param maxSegmentSize The maximum segment size in bytes.
     * @return The storage builder.
     * @throws IllegalArgumentException If the {@code maxSegmentSize} is not positive
     */
    public Builder withMaxSegmentSize(final int maxSegmentSize) {
      this.maxSegmentSize = maxSegmentSize;
      return this;
    }

    /**
     * Sets the percentage of free disk space that must be preserved before log compaction is
     * forced.
     *
     * @param freeDiskSpace the free disk percentage
     * @return the Raft log builder
     */
    public Builder withFreeDiskSpace(final long freeDiskSpace) {
      checkArgument(freeDiskSpace >= 0, "freeDiskSpace must be positive");
      this.freeDiskSpace = freeDiskSpace;
      return this;
    }

    /**
     * Sets the {@link RaftLogFlusher.Factory} to create a new flushing strategy for the {@link
     * RaftLog} when {@link #openLog(MetaStore, ThreadContext)} is called.
     *
     * @param flusherFactory factory to create the flushing strategy for the {@link RaftLog}
     * @return the storage builder.
     */
    public Builder withFlusherFactory(final RaftLogFlusher.Factory flusherFactory) {
      this.flusherFactory = flusherFactory;
      return this;
    }

    /**
     * Sets the snapshot store to use for remote snapshot installation.
     *
     * @param persistedSnapshotStore the snapshot store for this Raft
     * @return the storage builder
     */
    public Builder withSnapshotStore(final ReceivableSnapshotStore persistedSnapshotStore) {
      this.persistedSnapshotStore = persistedSnapshotStore;
      return this;
    }

    public Builder withJournalIndexDensity(final int journalIndexDensity) {
      this.journalIndexDensity = journalIndexDensity;
      return this;
    }

    /**
     * Sets whether segment files are pre-allocated at creation. If true, segment files are
     * pre-allocated to the maximum segment size (see {@link #withMaxSegmentSize(int)}}) at creation
     * before any writes happen.
     *
     * @param preallocateSegmentFiles true to preallocate files, false otherwise
     * @return this builder for chaining
     */
    public Builder withPreallocateSegmentFiles(final boolean preallocateSegmentFiles) {
      this.preallocateSegmentFiles = preallocateSegmentFiles;
      return this;
    }

    /**
     * The ID of the partition on which this storage resides.
     *
     * @param partitionId the storage's partition ID
     * @return this builder for chaining
     */
    public Builder withPartitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    /**
     * Builds the {@link RaftStorage} object.
     *
     * @return The built storage configuration.
     */
    @Override
    public RaftStorage build() {
      return new RaftStorage(
          prefix,
          partitionId,
          directory,
          maxSegmentSize,
          freeDiskSpace,
          flusherFactory,
          persistedSnapshotStore,
          journalIndexDensity,
          preallocateSegmentFiles);
    }
  }
}
