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
package io.atomix.raft.storage.log;

import io.atomix.raft.storage.log.RaftLogFlusher.DirectFlusher;
import io.atomix.raft.storage.log.RaftLogFlusher.Factory;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.journal.file.SegmentedJournalBuilder;
import java.io.File;

public class RaftLogBuilder implements io.atomix.utils.Builder<RaftLog> {

  private final SegmentedJournalBuilder journalBuilder = SegmentedJournal.builder();
  private RaftLogFlusher flusher = Factory.DIRECT;

  protected RaftLogBuilder() {}

  /**
   * Sets the storage name.
   *
   * @param name The storage name.
   * @return The storage builder.
   */
  public RaftLogBuilder withName(final String name) {
    journalBuilder.withName(name);
    return this;
  }

  /**
   * Sets the log directory, returning the builder for method chaining.
   *
   * <p>The log will write segment files into the provided directory.
   *
   * @param directory The log directory.
   * @return The storage builder.
   * @throws NullPointerException If the {@code directory} is {@code null}
   */
  public RaftLogBuilder withDirectory(final File directory) {
    journalBuilder.withDirectory(directory);
    return this;
  }

  /**
   * Sets the maximum segment size in bytes, returning the builder for method chaining.
   *
   * <p>The maximum segment size dictates when logs should roll over to new segments. As entries are
   * written to a segment of the log, once the size of the segment surpasses the configured maximum
   * segment size, the log will create a new segment and append new entries to that segment.
   *
   * <p>By default, the maximum segment size is {@code 1024 * 1024 * 32}.
   *
   * @param maxSegmentSize The maximum segment size in bytes.
   * @return The storage builder.
   * @throws IllegalArgumentException If the {@code maxSegmentSize} is not positive
   */
  public RaftLogBuilder withMaxSegmentSize(final int maxSegmentSize) {
    journalBuilder.withMaxSegmentSize(maxSegmentSize);
    return this;
  }

  /**
   * Sets the minimum free disk space to leave when allocating a new segment
   *
   * @param freeDiskSpace free disk space in bytes
   * @return the storage builder
   * @throws IllegalArgumentException if the {@code freeDiskSpace} is not positive
   */
  public RaftLogBuilder withFreeDiskSpace(final long freeDiskSpace) {
    journalBuilder.withFreeDiskSpace(freeDiskSpace);
    return this;
  }

  /**
   * Sets the flushing strategy. See implementations of {@link RaftLogFlusher} for which to use.
   * Each strategy provides different guarantees to allow for a trade-off between performance and
   * safety.
   *
   * @param flusher the flushing strategy, defaults to {@link DirectFlusher}
   * @return this builder for chaining
   */
  public RaftLogBuilder withFlusher(final RaftLogFlusher flusher) {
    this.flusher = flusher;
    return this;
  }

  /**
   * Sets the index density of the journal.
   *
   * <p>When journalIndexDensity is set to n, every n'th record is indexed. So higher this value,
   * longer a seek operation takes. Lower this value more memory is required to store the index
   * mappings.
   *
   * @param journalIndexDensity the journal index density
   * @return this builder for chaining
   */
  public RaftLogBuilder withJournalIndexDensity(final int journalIndexDensity) {
    journalBuilder.withJournalIndexDensity(journalIndexDensity);
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
  public RaftLogBuilder withPreallocateSegmentFiles(final boolean preallocateSegmentFiles) {
    journalBuilder.withPreallocateSegmentFiles(preallocateSegmentFiles);
    return this;
  }

  /**
   * The ID of the partition on which this log resides.
   *
   * @param partitionId the log's partition ID
   * @return this builder for chaining
   */
  public RaftLogBuilder withPartitionId(final int partitionId) {
    journalBuilder.withPartitionId(partitionId);
    return this;
  }

  /**
   * @param metaStore A persisted JournalMetaStore that can store lastFlushedIndex.
   * @return this builder for chaining
   */
  public RaftLogBuilder withMetaStore(final JournalMetaStore metaStore) {
    journalBuilder.withMetaStore(metaStore);
    return this;
  }

  @Override
  public RaftLog build() {
    final Journal journal = journalBuilder.build();
    return new RaftLog(journal, flusher);
  }
}
