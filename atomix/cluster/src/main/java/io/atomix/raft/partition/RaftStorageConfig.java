/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.raft.partition;

import static com.google.common.base.Preconditions.checkNotNull;

import com.esotericsoftware.kryo.serializers.FieldSerializer.Optional;
import io.atomix.storage.StorageLevel;
import io.atomix.utils.memory.MemorySize;
import io.zeebe.snapshots.raft.ReceivableSnapshotStoreFactory;

/** Raft storage configuration. */
public class RaftStorageConfig {

  private static final String DATA_PREFIX = ".data";
  private static final StorageLevel DEFAULT_STORAGE_LEVEL = StorageLevel.DISK;
  private static final int DEFAULT_MAX_SEGMENT_SIZE = 1024 * 1024 * 32;
  private static final int DEFAULT_MAX_ENTRY_SIZE = 1024 * 1024;
  private static final boolean DEFAULT_FLUSH_EXPLICITLY = true;
  private static final long DEFAULT_FREE_DISK_SPACE = 1024L * 1024 * 1024;
  private static final int DEFAULT_JOURNAL_INDEX_DENSITY = 100;

  private String directory;
  private StorageLevel level = DEFAULT_STORAGE_LEVEL;
  private int maxEntrySize = DEFAULT_MAX_ENTRY_SIZE;
  private long segmentSize = DEFAULT_MAX_SEGMENT_SIZE;
  private boolean flushExplicitly = DEFAULT_FLUSH_EXPLICITLY;
  private long freeDiskSpace = DEFAULT_FREE_DISK_SPACE;
  private int journalIndexDensity = DEFAULT_JOURNAL_INDEX_DENSITY;

  @Optional("SnapshotStoreFactory")
  private ReceivableSnapshotStoreFactory persistedSnapshotStoreFactory;

  /**
   * Returns the partition data directory.
   *
   * @param groupName the partition group name
   * @return the partition data directory
   */
  public String getDirectory(final String groupName) {
    return directory != null
        ? directory
        : System.getProperty("atomix.data", DATA_PREFIX) + "/" + groupName;
  }

  /**
   * Returns the partition storage level.
   *
   * @return the partition storage level
   */
  public StorageLevel getLevel() {
    return level;
  }

  /**
   * Sets the partition storage level.
   *
   * @param storageLevel the partition storage level
   * @return the Raft partition group configuration
   */
  public RaftStorageConfig setLevel(final StorageLevel storageLevel) {
    level = checkNotNull(storageLevel);
    return this;
  }

  /**
   * Returns the maximum entry size.
   *
   * @return the maximum entry size
   */
  public MemorySize getMaxEntrySize() {
    return MemorySize.from(maxEntrySize);
  }

  /**
   * Sets the maximum entry size.
   *
   * @param maxEntrySize the maximum entry size
   * @return the Raft storage configuration
   */
  public RaftStorageConfig setMaxEntrySize(final MemorySize maxEntrySize) {
    this.maxEntrySize = (int) maxEntrySize.bytes();
    return this;
  }

  /**
   * Returns the Raft log segment size.
   *
   * @return the Raft log segment size
   */
  public MemorySize getSegmentSize() {
    return MemorySize.from(segmentSize);
  }

  /**
   * Sets the Raft log segment size.
   *
   * @param segmentSize the Raft log segment size
   * @return the partition group configuration
   */
  public RaftStorageConfig setSegmentSize(final MemorySize segmentSize) {
    this.segmentSize = segmentSize.bytes();
    return this;
  }

  /**
   * Returns whether to flush logs to disk to guarantee correctness. If true, followers will flush
   * on every append, and the leader will flush on commit.
   *
   * @return whether to flush logs to disk
   */
  public boolean shouldFlushExplicitly() {
    return flushExplicitly;
  }

  /**
   * Sets whether to flush logs to disk to guarantee correctness. If true, followers will flush on
   * every append, and the leader will flush on commit.
   *
   * @param flushExplicitly whether to flush logs to disk
   * @return the Raft partition group configuration
   */
  public RaftStorageConfig setFlushExplicitly(final boolean flushExplicitly) {
    this.flushExplicitly = flushExplicitly;
    return this;
  }

  /**
   * Sets the partition data directory.
   *
   * @param directory the partition data directory
   * @return the Raft partition group configuration
   */
  public RaftStorageConfig setDirectory(final String directory) {
    this.directory = directory;
    return this;
  }

  /**
   * Returns the current snapshot store factory.
   *
   * @return the snapshot store factory
   */
  public ReceivableSnapshotStoreFactory getPersistedSnapshotStoreFactory() {
    return persistedSnapshotStoreFactory;
  }

  /**
   * Sets the snapshot store factory.
   *
   * @param persistedSnapshotStoreFactory the new snapshot store factory
   * @return the Raft storage configuration
   */
  public RaftStorageConfig setPersistedSnapshotStoreFactory(
      final ReceivableSnapshotStoreFactory persistedSnapshotStoreFactory) {
    this.persistedSnapshotStoreFactory = persistedSnapshotStoreFactory;
    return this;
  }

  /**
   * Returns the minimum free disk space buffer to leave when allocating a new segment
   *
   * @return free disk buffer
   */
  public long getFreeDiskSpace() {
    return freeDiskSpace;
  }

  /**
   * Sets the minimum free disk space buffer
   *
   * @param freeDiskSpace
   * @return
   */
  public RaftStorageConfig setFreeDiskSpace(final long freeDiskSpace) {
    this.freeDiskSpace = freeDiskSpace;
    return this;
  }

  public int getJournalIndexDensity() {
    return journalIndexDensity;
  }

  public RaftStorageConfig setJournalIndexDensity(final int journalIndexDensity) {
    this.journalIndexDensity = journalIndexDensity;
    return this;
  }
}
