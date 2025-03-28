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
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A file based journal. The journal is split into multiple segments files. */
public final class SegmentedJournal implements Journal {
  public static final long ASQN_IGNORE = -1;
  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentedJournal.class);
  private final JournalMetrics journalMetrics;
  private final Collection<SegmentedJournalReader> readers = Sets.newConcurrentHashSet();
  private volatile boolean open = true;
  private final JournalIndex journalIndex;
  private final SegmentedJournalWriter writer;
  private final StampedLock rwlock = new StampedLock();
  private final SegmentsManager segments;

  SegmentedJournal(
      final JournalIndex journalIndex,
      final SegmentsManager segments,
      final JournalMetrics journalMetrics,
      final SegmentsFlusher segmentsFlusher) {
    this.journalMetrics = Objects.requireNonNull(journalMetrics, "must specify journal metrics");
    this.journalIndex = Objects.requireNonNull(journalIndex, "must specify a journal index");
    this.segments = Objects.requireNonNull(segments, "must specify a journal segments manager");
    Objects.requireNonNull(segmentsFlusher, "must specify a segments flusher");

    this.segments.open();
    writer = new SegmentedJournalWriter(segments, segmentsFlusher, journalMetrics);
  }

  /**
   * Returns a new SegmentedJournal builder.
   *
   * @return A new Segmented journal builder.
   */
  public static SegmentedJournalBuilder builder(final MeterRegistry meterRegistry) {
    return new SegmentedJournalBuilder(meterRegistry);
  }

  @Override
  public JournalRecord append(final BufferWriter recordDataWriter) {
    return append(ASQN_IGNORE, recordDataWriter);
  }

  @Override
  public JournalRecord append(final long asqn, final BufferWriter recordDataWriter) {
    try (final var ignored = journalMetrics.observeAppendLatency()) {
      return writer.append(asqn, recordDataWriter);
    }
  }

  @Override
  public void append(final JournalRecord record) {
    try (final var ignored = journalMetrics.observeAppendLatency()) {
      writer.append(record);
    }
  }

  @Override
  public JournalRecord append(final long checksum, final byte[] serializedRecord) {
    try (final var ignored = journalMetrics.observeAppendLatency()) {
      return writer.append(checksum, serializedRecord);
    }
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
  public boolean deleteUntil(final long index) {
    final var stamp = rwlock.writeLock();
    try {
      return segments.deleteUntil(index);
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
      // no need to update the meta store's last flushed index as usage is that we always reset
      // with a greater index than what we previously had. it's fine if the stored last flushed
      // index is lower than the real flushed index. every thing will be treated as a partial write
      // until the next flush, which is fine
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
    return writer.getNextIndex() - getFirstIndex() == 0;
  }

  @Override
  public void flush() {
    if (!isOpen() || isEmpty()) {
      LOGGER.debug("Skipped journal flush as it is either closed or empty");
      return;
    }

    try (final var ignored = journalMetrics.observeJournalFlush()) {
      // grabbing the read lock here will prevent write-exclusive operations such as deleteAfter and
      // reset from modifying the segments, allowing us to properly determine which segments must be
      // flushed. contention is quite low as it only contends with deleteAfter, deleteUntil, and
      // reset, all operations which do not run often, and not on the hot path. in the case where
      // flushing is synchronous on the raft thread (the default), then all these operations run
      // sequentially anyway, meaning there is virtually no contention
      final var stamp = rwlock.readLock();
      try {
        writer.flush();
      } finally {
        rwlock.unlockRead(stamp);
      }
    }
  }

  @Override
  public JournalReader openReader() {
    final var stamped = acquireReadlock();
    try {
      final var reader = new SegmentedJournalReader(this, journalMetrics);
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
  public SortedMap<Long, Path> getTailSegments(final long index) {
    final var tailSegments = segments.getTailSegments(index);
    final var treeMap =
        tailSegments.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    e -> e.getValue().file().file().toPath(),
                    (a, b) -> b,
                    TreeMap::new));

    return Collections.unmodifiableSortedMap(treeMap);
  }

  @Override
  public void close() {
    flush();
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

  public JournalIndex getJournalIndex() {
    return journalIndex;
  }

  long acquireReadlock() {
    return rwlock.readLock();
  }

  void releaseReadlock(final long stamp) {
    rwlock.unlockRead(stamp);
  }

  @VisibleForTesting(
      "The simplest way to guarantee certain methods acquire/release the write lock is to access directly")
  StampedLock rwlock() {
    return rwlock;
  }
}
