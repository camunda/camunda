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
import io.atomix.raft.storage.snapshot.SnapshotStoreFactory;
import io.atomix.raft.storage.snapshot.impl.DefaultSnapshotStore;
import io.atomix.storage.StorageLevel;
import io.atomix.utils.memory.MemorySize;

/** Raft storage configuration. */
public class RaftStorageConfig {

  private static final String DATA_PREFIX = ".data";
  private static final StorageLevel DEFAULT_STORAGE_LEVEL = StorageLevel.DISK;
  private static final int DEFAULT_MAX_SEGMENT_SIZE = 1024 * 1024 * 32;
  private static final int DEFAULT_MAX_ENTRY_SIZE = 1024 * 1024;
  private static final boolean DEFAULT_FLUSH_ON_COMMIT = false;
  private static final SnapshotStoreFactory DEFAULT_SNAPSHOT_STORE_FACTORY =
      DefaultSnapshotStore::new;

  private String directory;
  private StorageLevel level = DEFAULT_STORAGE_LEVEL;
  private int maxEntrySize = DEFAULT_MAX_ENTRY_SIZE;
  private long segmentSize = DEFAULT_MAX_SEGMENT_SIZE;
  private boolean flushOnCommit = DEFAULT_FLUSH_ON_COMMIT;

  @Optional("SnapshotStoreFactory")
  private SnapshotStoreFactory snapshotStoreFactory = DEFAULT_SNAPSHOT_STORE_FACTORY;

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
    this.level = checkNotNull(storageLevel);
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
   * Returns whether to flush logs to disk on commit.
   *
   * @return whether to flush logs to disk on commit
   */
  public boolean isFlushOnCommit() {
    return flushOnCommit;
  }

  /**
   * Sets whether to flush logs to disk on commit.
   *
   * @param flushOnCommit whether to flush logs to disk on commit
   * @return the Raft partition group configuration
   */
  public RaftStorageConfig setFlushOnCommit(final boolean flushOnCommit) {
    this.flushOnCommit = flushOnCommit;
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
  public SnapshotStoreFactory getSnapshotStoreFactory() {
    return snapshotStoreFactory;
  }

  /**
   * Sets the snapshot store factory.
   *
   * @param snapshotStoreFactory the new snapshot store factory
   * @return the Raft storage configuration
   */
  public RaftStorageConfig setSnapshotStoreFactory(
      final SnapshotStoreFactory snapshotStoreFactory) {
    this.snapshotStoreFactory = snapshotStoreFactory;
    return this;
  }
}
