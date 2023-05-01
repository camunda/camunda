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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Sets;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.locks.StampedLock;
import org.agrona.DirectBuffer;

/** A file based journal. The journal is split into multiple segments files. */
public final class SegmentedJournal implements Journal {
  public static final long ASQN_IGNORE = -1;
  private static final int SEGMENT_BUFFER_FACTOR = 3;
  private final JournalMetrics journalMetrics;
  private final File directory;
  private final int maxSegmentSize;
  private final Collection<SegmentedJournalReader> readers = Sets.newConcurrentHashSet();
  private volatile boolean open = true;
  private final long minFreeDiskSpace;
  private final JournalIndex journalIndex;
  private final SegmentedJournalWriter writer;
  private final StampedLock rwlock = new StampedLock();
  private final SegmentsManager segments;

  private final JournalMetaStore journalMetaStore;

  SegmentedJournal(
      final File directory,
      final int maxSegmentSize,
      final long minFreeDiskSpace,
      final JournalIndex journalIndex,
      final SegmentsManager segments,
      final JournalMetrics journalMetrics,
      final JournalMetaStore journalMetaStore) {
    this.directory = Objects.requireNonNull(directory, "must specify a journal directory");
    this.maxSegmentSize = maxSegmentSize;
    this.minFreeDiskSpace = minFreeDiskSpace;
    this.journalMetrics = Objects.requireNonNull(journalMetrics, "must specify journal metrics");
    this.journalIndex = Objects.requireNonNull(journalIndex, "must specify a journal index");
    this.segments = Objects.requireNonNull(segments, "must specify a journal segments manager");
    this.journalMetaStore =
        Objects.requireNonNull(journalMetaStore, "must specify a journal meta store");

    this.segments.open();
    writer = new SegmentedJournalWriter(this);
  }

  /**
   * Returns a new SegmentedJournal builder.
   *
   * @return A new Segmented journal builder.
   */
  public static SegmentedJournalBuilder builder() {
    return new SegmentedJournalBuilder();
  }

  @Override
  public JournalRecord append(final long asqn, final DirectBuffer data) {
    return writer.append(asqn, data);
  }

  @Override
  public JournalRecord append(final DirectBuffer data) {
    return writer.append(ASQN_IGNORE, data);
  }

  @Override
  public void append(final JournalRecord record) {
    writer.append(record);
  }

  @Override
  public void deleteAfter(final long indexExclusive) {
    journalMetrics.observeSegmentTruncation(
        () -> {
          final var stamp = rwlock.writeLock();
          try {
            writer.deleteAfter(indexExclusive);
            // Reset segment readers.
            resetAdvancedReaders(indexExclusive + 1);
          } finally {
            rwlock.unlockWrite(stamp);
          }
        });
  }

  @Override
  public void deleteUntil(final long index) {
    final var stamp = rwlock.writeLock();
    try {
      segments.deleteUntil(index);
    } finally {
      rwlock.unlockWrite(stamp);
    }
  }

  @Override
  public void reset(final long nextIndex) {
    final var stamp = rwlock.writeLock();
    try {
      journalIndex.clear();
      writer.reset(nextIndex);
    } finally {
      rwlock.unlockWrite(stamp);
    }
  }

  @Override
  public long getLastIndex() {
    return writer.getLastIndex();
  }

  @Override
  public long getFirstIndex() {
    final var firstSegment = segments.getFirstSegment();
    return firstSegment != null ? firstSegment.index() : 0;
  }

  @Override
  public boolean isEmpty() {
    return writer.getNextIndex() - getFirstSegment().index() == 0;
  }

  @Override
  public void flush() {
    writer.flush();
    journalMetaStore.storeLastFlushedIndex(getLastIndex());
  }

  @Override
  public JournalReader openReader() {
    final var stamped = acquireReadlock();
    try {
      final var reader = new SegmentedJournalReader(this);
      readers.add(reader);
      return reader;
    } finally {
      releaseReadlock(stamped);
    }
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    segments.close();
    open = false;
  }

  /**
   * Asserts that the journal is open.
   *
   * @throws IllegalStateException if the journal is not open
   */
  private void assertOpen() {
    checkState(segments.getCurrentSegment() != null, "journal not open");
  }

  /** Asserts that enough disk space is available to allocate a new segment. */
  private void assertDiskSpace() {
    if (directory().getUsableSpace()
        < Math.max(maxSegmentSize() * SEGMENT_BUFFER_FACTOR, minFreeDiskSpace)) {
      throw new JournalException.OutOfDiskSpace(
          "Not enough space to allocate a new journal segment");
    }
  }

  private long maxSegmentSize() {
    return maxSegmentSize;
  }

  private File directory() {
    return directory;
  }

  /**
   * Returns the first segment in the log.
   *
   * @throws IllegalStateException if the segment manager is not open
   */
  Segment getFirstSegment() {
    assertOpen();
    return segments.getFirstSegment();
  }

  /**
   * Returns the last segment in the log.
   *
   * @throws IllegalStateException if the segment manager is not open
   */
  Segment getLastSegment() {
    assertOpen();
    return segments.getLastSegment();
  }

  Segment getNextSegment() {
    assertOpen();
    assertDiskSpace();
    return segments.getNextSegment();
  }

  /**
   * Returns the segment following the segment with the given ID.
   *
   * @param index The segment index with which to look up the next segment.
   * @return The next segment for the given index.
   */
  Segment getNextSegment(final long index) {
    return segments.getNextSegment(index);
  }

  /**
   * Returns the segment for the given index.
   *
   * @param index The index for which to return the segment.
   * @throws IllegalStateException if the segment manager is not open
   */
  Segment getSegment(final long index) {
    assertOpen();
    return segments.getSegment(index);
  }

  public void closeReader(final SegmentedJournalReader segmentedJournalReader) {
    readers.remove(segmentedJournalReader);
  }

  /**
   * Resets and returns the first segment in the journal.
   *
   * @param index the starting index of the journal
   * @return the first segment
   */
  Segment resetSegments(final long index) {
    return segments.resetSegments(index);
  }

  /**
   * Removes a segment.
   *
   * @param segment The segment to remove.
   */
  synchronized void removeSegment(final Segment segment) {
    segments.removeSegment(segment);
  }
  /**
   * Resets journal readers to the given index, if they are at a larger index.
   *
   * @param index The index at which to reset readers.
   */
  void resetAdvancedReaders(final long index) {
    for (final SegmentedJournalReader reader : readers) {
      if (reader.getNextIndex() > index) {
        reader.unsafeSeek(index);
      }
    }
  }

  public JournalMetrics getJournalMetrics() {
    return journalMetrics;
  }

  public JournalIndex getJournalIndex() {
    return journalIndex;
  }

  long acquireReadlock() {
    return rwlock.readLock();
  }

  void releaseReadlock(final long stamp) {
    rwlock.unlockRead(stamp);
  }
}
