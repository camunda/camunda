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

import com.esotericsoftware.kryo.serializers.FieldSerializer.Optional;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogFlusher;
import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStoreFactory;

/** Raft storage configuration. */
public class RaftStorageConfig {

  private static final String DATA_PREFIX = ".data";
  private static final int DEFAULT_MAX_SEGMENT_SIZE = 1024 * 1024 * 32;
  private static final RaftLogFlusher.Factory DEFAULT_FLUSHER_FACTORY =
      RaftLogFlusher.Factory::direct;
  private static final long DEFAULT_FREE_DISK_SPACE = 1024L * 1024 * 1024;
  private static final int DEFAULT_JOURNAL_INDEX_DENSITY = 100;

  private static final boolean DEFAULT_PREALLOCATE_SEGMENT_FILES = true;

  private String directory;
  private long segmentSize = DEFAULT_MAX_SEGMENT_SIZE;
  private RaftLogFlusher.Factory flusherFactory = DEFAULT_FLUSHER_FACTORY;
  private long freeDiskSpace = DEFAULT_FREE_DISK_SPACE;
  private int journalIndexDensity = DEFAULT_JOURNAL_INDEX_DENSITY;
  private boolean preallocateSegmentFiles = DEFAULT_PREALLOCATE_SEGMENT_FILES;

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
   * Returns the Raft log segment size.
   *
   * @return the Raft log segment size
   */
  public long getSegmentSize() {
    return segmentSize;
  }

  /**
   * Sets the Raft log segment size.
   *
   * @param segmentSizeBytes the Raft log segment size
   * @return the partition group configuration
   */
  public RaftStorageConfig setSegmentSize(final long segmentSizeBytes) {
    segmentSize = segmentSizeBytes;
    return this;
  }

  /**
   * Returns the {@link RaftLogFlusher.Factory} to create a new flushing strategy for the {@link
   * RaftLog} when * {@link io.atomix.raft.storage.RaftStorage#openLog(ThreadContext)} is called.
   *
   * @return the flusher factory for this storage
   */
  public RaftLogFlusher.Factory flusherFactory() {
    return flusherFactory;
  }

  /**
   * Sets the {@link RaftLogFlusher.Factory} to create a new flushing strategy for the {@link
   * RaftLog} when {@link io.atomix.raft.storage.RaftStorage#openLog(ThreadContext)} is called.
   *
   * @param flusherFactory factory to create the flushing strategy for the {@link RaftLog}
   * @return the Raft partition group configuration
   */
  public RaftStorageConfig setFlusherFactory(final RaftLogFlusher.Factory flusherFactory) {
    this.flusherFactory = flusherFactory;
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

  /**
   * @return true to preallocate segment files, false otherwise
   */
  public boolean isPreallocateSegmentFiles() {
    return preallocateSegmentFiles;
  }

  /**
   * Sets whether segment files are pre-allocated at creation. If true, segment files are
   * pre-allocated to {@link #segmentSize} at creation before any writes happen.
   *
   * @param preallocateSegmentFiles true to preallocate files, false otherwise
   */
  public void setPreallocateSegmentFiles(final boolean preallocateSegmentFiles) {
    this.preallocateSegmentFiles = preallocateSegmentFiles;
  }

  @Override
  public String toString() {
    return "RaftStorageConfig{"
        + "directory='"
        + directory
        + '\''
        + ", segmentSize="
        + segmentSize
        + ", flushExplicitly="
        + flusherFactory
        + ", freeDiskSpace="
        + freeDiskSpace
        + ", journalIndexDensity="
        + journalIndexDensity
        + ", preallocateSegmentFiles="
        + preallocateSegmentFiles
        + ", persistedSnapshotStoreFactory="
        + persistedSnapshotStoreFactory
        + '}';
  }
}
