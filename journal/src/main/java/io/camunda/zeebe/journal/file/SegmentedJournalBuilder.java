/*
 * Copyright 2017-present Open Networking Foundation
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
package io.camunda.zeebe.journal.file;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.camunda.zeebe.journal.JournalMetaStore;
import java.io.File;

/** Raft log builder. */
@SuppressWarnings("UnusedReturnValue")
public class SegmentedJournalBuilder {

  private static final String DEFAULT_NAME = "journal";
  private static final String DEFAULT_DIRECTORY = System.getProperty("user.dir");
  private static final int DEFAULT_MAX_SEGMENT_SIZE = 1024 * 1024 * 32;
  private static final long DEFAULT_MIN_FREE_DISK_SPACE = 1024L * 1024 * 1024;
  private static final int DEFAULT_JOURNAL_INDEX_DENSITY = 100;
  private static final boolean DEFAULT_PREALLOCATE_SEGMENT_FILES = true;

  protected String name = DEFAULT_NAME;
  protected File directory = new File(DEFAULT_DIRECTORY);
  protected int maxSegmentSize = DEFAULT_MAX_SEGMENT_SIZE;

  private long freeDiskSpace = DEFAULT_MIN_FREE_DISK_SPACE;
  private int journalIndexDensity = DEFAULT_JOURNAL_INDEX_DENSITY;
  private boolean preallocateSegmentFiles = DEFAULT_PREALLOCATE_SEGMENT_FILES;

  private JournalMetaStore journalMetaStore;

  protected SegmentedJournalBuilder() {}

  /**
   * Sets the storage name.
   *
   * @param name The storage name.
   * @return The storage builder.
   */
  public SegmentedJournalBuilder withName(final String name) {
    this.name = checkNotNull(name, "name cannot be null");
    return this;
  }

  /**
   * Sets the journal directory, returning the builder for method chaining.
   *
   * <p>The journal will write segment files into the provided directory.
   *
   * @param directory The log directory.
   * @return The storage builder.
   * @throws NullPointerException If the {@code directory} is {@code null}
   */
  public SegmentedJournalBuilder withDirectory(final String directory) {
    return withDirectory(new File(checkNotNull(directory, "directory cannot be null")));
  }

  /**
   * Sets the journal directory, returning the builder for method chaining.
   *
   * <p>The journal will write segment files into the provided directory.
   *
   * @param directory The journal directory.
   * @return The journal builder.
   * @throws NullPointerException If the {@code directory} is {@code null}
   */
  public SegmentedJournalBuilder withDirectory(final File directory) {
    this.directory = checkNotNull(directory, "directory cannot be null");
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
   * @return The journal builder.
   * @throws IllegalArgumentException If the {@code maxSegmentSize} is not positive
   */
  public SegmentedJournalBuilder withMaxSegmentSize(final int maxSegmentSize) {
    checkArgument(
        maxSegmentSize > SegmentDescriptor.getEncodingLength(),
        "maxSegmentSize must be greater than " + SegmentDescriptor.getEncodingLength());
    this.maxSegmentSize = maxSegmentSize;
    return this;
  }

  /**
   * Sets the minimum free disk space to leave when allocating a new segment
   *
   * @param freeDiskSpace free disk space in bytes
   * @return the storage builder
   * @throws IllegalArgumentException if the {@code freeDiskSpace} is not positive
   */
  public SegmentedJournalBuilder withFreeDiskSpace(final long freeDiskSpace) {
    checkArgument(freeDiskSpace >= 0, "minFreeDiskSpace must be positive");
    this.freeDiskSpace = freeDiskSpace;
    return this;
  }

  public SegmentedJournalBuilder withJournalIndexDensity(final int journalIndexDensity) {
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
  public SegmentedJournalBuilder withPreallocateSegmentFiles(
      final boolean preallocateSegmentFiles) {
    this.preallocateSegmentFiles = preallocateSegmentFiles;
    return this;
  }

  /**
   * @param metaStore journal metastore to update lastFlushedIndex
   * @return this builder for chaining
   */
  public SegmentedJournalBuilder withMetaStore(final JournalMetaStore metaStore) {
    journalMetaStore = metaStore;
    return this;
  }

  public SegmentedJournal build() {
    final var journalIndex = new SparseJournalIndex(journalIndexDensity);
    final var segmentAllocator =
        preallocateSegmentFiles ? SegmentAllocator.fill() : SegmentAllocator.noop();
    final var segmentLoader = new SegmentLoader(segmentAllocator, freeDiskSpace);
    final var segmentsManager =
        new SegmentsManager(
            journalIndex, maxSegmentSize, directory, name, segmentLoader, journalMetaStore);
    final var journalMetrics = new JournalMetrics(name);

    return new SegmentedJournal(journalIndex, segmentsManager, journalMetrics, journalMetaStore);
  }
}
