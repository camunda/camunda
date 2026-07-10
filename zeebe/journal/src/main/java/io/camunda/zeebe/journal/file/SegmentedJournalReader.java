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

import static io.camunda.zeebe.journal.file.SegmentedJournal.ASQN_IGNORE;

import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.RecordMetadata;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.util.IndexEntry;
import io.camunda.zeebe.util.IndexScanResult;
import io.camunda.zeebe.util.JournalIndexCursor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

class SegmentedJournalReader implements JournalReader {

  private final SegmentedJournal journal;
  private Segment currentSegment;
  private SegmentReader currentReader;
  private final JournalMetrics metrics;
  private final @Nullable JournalIndexCursor indexCursor;
  private final SBESerializer scanSerializer = new SBESerializer();

  SegmentedJournalReader(final SegmentedJournal journal, final JournalMetrics journalMetrics) {
    this.journal = journal;
    metrics = journalMetrics;
    indexCursor = journal.createIndexCursor();
    initialize();
  }

  /** Initializes the reader to the given index. */
  private void initialize() {
    currentSegment = journal.getFirstSegment();
    currentReader = currentSegment.createReader();
  }

  @Override
  public boolean hasNext() {
    final var stamp = journal.acquireReadlock();
    try {
      return unsafeHasNext();
    } finally {
      journal.releaseReadlock(stamp);
    }
  }

  @Override
  public JournalRecord next() {
    final var stamp = journal.acquireReadlock();
    try {
      return unsafeNext();
    } finally {
      journal.releaseReadlock(stamp);
    }
  }

  private JournalRecord unsafeNext() throws NoSuchElementException {
    if (!unsafeHasNext()) {
      throw new NoSuchElementException();
    }

    return currentReader.next();
  }

  @Override
  public long seek(final long index) {
    try (final var ignored = metrics.observeSeekLatency()) {
      final var stamp = journal.acquireReadlock();
      try {
        // If the current segment is not open, it has been replaced. Reset the segments.
        return unsafeSeek(index);
      } finally {
        journal.releaseReadlock(stamp);
      }
    }
  }

  @Override
  public long seekToFirst() {
    try (final var ignored = metrics.observeSeekLatency()) {
      final var stamp = journal.acquireReadlock();
      try {
        return unsafeSeekToFirst();
      } finally {
        journal.releaseReadlock(stamp);
      }
    }
  }

  @Override
  public long seekToLast() {
    try (final var ignored = metrics.observeSeekLatency()) {
      final var stamp = journal.acquireReadlock();
      try {
        return unsafeSeekToLast();
      } finally {
        journal.releaseReadlock(stamp);
      }
    }
  }

  @Override
  public long seekToAsqn(final long asqn) {
    return seekToAsqn(asqn, journal.getLastIndex());
  }

  @Override
  public long seekToAsqn(final long asqn, final long indexUpperBound) {
    try (final var ignored = metrics.observeSeekLatency()) {
      final var stamp = journal.acquireReadlock();
      try {
        final var journalIndex = journal.getJournalIndex();
        final var index = journalIndex.lookupAsqn(asqn, indexUpperBound);

        // depending on the type of index, it's possible there is no ASQN indexed, in which case
        // start from the beginning
        if (index == null) {
          unsafeSeekToFirst();
        } else {
          unsafeSeek(index);
        }

        // potential beneficiary of a peek() call, which would avoid the duplicate seek or
        // being at the second position if the first entry has a greater ASQN
        JournalRecord record = null;
        while (unsafeHasNext()) {
          final var currentRecord = next();
          if (currentRecord.index() > indexUpperBound) {
            break;
          }
          if (currentRecord.asqn() <= asqn && currentRecord.asqn() != ASQN_IGNORE) {
            record = currentRecord;
          } else if (currentRecord.asqn() >= asqn) {
            break;
          }
        }

        // if the journal was empty, the reader will be at the beginning of the log
        // if the journal only contained entries with ASQN greater than the one requested, then seek
        // back to the beginning
        if (record == null) {
          return unsafeSeekToFirst();
        }

        // This is needed so that the next() returns the correct record
        // TODO: Remove the duplicate seek. https://github.com/zeebe-io/zeebe/issues/6223
        return unsafeSeek(record.index());
      } finally {
        journal.releaseReadlock(stamp);
      }
    }
  }

  @Override
  public IndexScanResult scanIndex(
      final long fromAsqn, final int maxBytes, final long upperBoundIndex) {
    final var stamp = journal.acquireReadlock();
    try {
      return unsafeScanIndex(fromAsqn, maxBytes, upperBoundIndex);
    } finally {
      journal.releaseReadlock(stamp);
    }
  }

  @Override
  public long getNextIndex() {
    return currentReader.getNextIndex();
  }

  @Override
  public void close() {
    currentReader.close();
    journal.closeReader(this);
  }

  private boolean isFirstSegment(final Segment segment) {
    return journal.getFirstSegment() == segment;
  }

  /**
   * Serves a fetch scan from the in-memory sparse index plus an on-demand walk over journal frames.
   * The sparse floor for {@code fromAsqn} yields a byte position at or before the record containing
   * it; from there the scan hops frames reading only their headers until it finds the first batch
   * with {@code highestAsqn >= fromAsqn}. Batches are then emitted as zero-copy slices until {@code
   * maxBytes} is reached, a record crosses {@code upperBoundIndex}, or the serving segment ends.
   * All emitted slices belong to a single segment, whose channel and lease are handed to the
   * caller.
   *
   * <p>Records passed by the walk are fed back into the sparse index, so a scan against a cold
   * index (e.g. right after a restart) warms it for subsequent scans.
   */
  private IndexScanResult unsafeScanIndex(
      final long fromAsqn, final int maxBytes, final long upperBoundIndex) {
    if (indexCursor == null) {
      // Without an application-entry cursor the journal cannot interpret record data as batches;
      // such journals never serve batch scans.
      return IndexScanResult.EndOfLog.INSTANCE;
    }

    final var journalIndex = journal.getJournalIndex();
    final Long floorIndex = journalIndex.lookupAsqn(fromAsqn, upperBoundIndex);
    // Only a walk that starts at the very beginning of the retained log can prove that the
    // requested ASQN precedes the oldest retained batch.
    final boolean walkFromLogStart = floorIndex == null;

    Segment segment =
        floorIndex == null ? journal.getFirstSegment() : journal.getSegment(floorIndex);
    if (segment == null) {
      throw new IllegalStateException(
          "Expected a segment containing index %d, but none was found".formatted(floorIndex));
    }

    final List<IndexEntry> entries = new ArrayList<>();
    int readBytes = 0;
    boolean firstBatchSeen = false;

    int startPosition = initialScanPosition(segment, floorIndex);
    while (segment != null && segment.isOpen()) {
      final ByteBuffer view = segment.createScanView();
      final DirectBuffer frameReader = new UnsafeBuffer(view);
      view.position(startPosition);

      while (FrameUtil.hasValidVersion(view)) {
        final int frameStart = view.position();
        final int metadataOffset = frameStart + FrameUtil.getLength();
        final RecordMetadata metadata = scanSerializer.readMetadata(frameReader, metadataOffset);
        final int recordOffset =
            metadataOffset + scanSerializer.getMetadataLength(frameReader, metadataOffset);
        final RecordData record = scanSerializer.readData(frameReader, recordOffset);
        final int nextFrame = recordOffset + metadata.length();

        // Self-warm the sparse index so the next scan starts closer to its floor.
        journalIndex.index(record.index(), record.asqn(), frameStart);

        if (record.asqn() != ASQN_IGNORE) {
          // The absolute offset of the record's data, i.e. right after the record's header.
          final int dataOffset = nextFrame - record.data().capacity();
          indexCursor.wrap(record.data(), dataOffset);
          while (indexCursor.hasNext()) {
            indexCursor.next();
            final long lowestAsqn = indexCursor.currentLowestAsqn();
            final long highestAsqn = indexCursor.currentHighestAsqn();

            if (!firstBatchSeen) {
              firstBatchSeen = true;
              if (walkFromLogStart && isFirstSegment(segment) && lowestAsqn > fromAsqn) {
                return IndexScanResult.Truncated.INSTANCE;
              }
            }

            if (highestAsqn < fromAsqn) {
              // The batch lies entirely before the requested ASQN.
              continue;
            }

            if (record.index() > upperBoundIndex) {
              // The batch exists but lies beyond the visibility bound (e.g. not yet committed).
              return entries.isEmpty()
                  ? IndexScanResult.FutureOffset.INSTANCE
                  : success(segment, entries);
            }

            entries.add(
                new IndexEntry(
                    lowestAsqn,
                    highestAsqn,
                    record.index(),
                    indexCursor.currentOffset(),
                    indexCursor.currentLength()));
            readBytes += indexCursor.currentLength();
            if (readBytes >= maxBytes) {
              return success(segment, entries);
            }
          }
        }

        view.position(nextFrame);
      }

      if (!entries.isEmpty()) {
        // The response is always served from a single segment.
        return success(segment, entries);
      }

      segment = journal.getNextSegment(segment.index());
      startPosition = segment != null ? segment.descriptor().encodingLength() : 0;
    }

    return IndexScanResult.EndOfLog.INSTANCE;
  }

  private IndexScanResult success(final Segment segment, final List<IndexEntry> entries) {
    return new IndexScanResult.Success(segment.channel(), entries, segment.retain());
  }

  /**
   * Returns the byte position at which the scan walk enters the given segment: the sparse index
   * position of the floor record if it lies within the segment, otherwise the segment start.
   */
  private int initialScanPosition(final Segment segment, final @Nullable Long floorIndex) {
    final int segmentStart = segment.descriptor().encodingLength();
    if (floorIndex == null) {
      return segmentStart;
    }

    final var floorPosition = journal.getJournalIndex().lookup(floorIndex);
    if (floorPosition != null
        && floorPosition.index() >= segment.index()
        && floorPosition.index() <= segment.lastIndex()) {
      return floorPosition.position();
    }

    return segmentStart;
  }

  long unsafeSeek(final long index) {
    if (!currentSegment.isOpen()) {
      unsafeSeekToFirst();
    }

    if (index < currentReader.getNextIndex()) {
      rewind(index);
    } else if (index > currentReader.getNextIndex()) {
      forward(index);
    } else {
      currentReader.seek(index);
    }

    return getNextIndex();
  }

  private long unsafeSeekToFirst() {
    replaceCurrentSegment(journal.getFirstSegment());
    return journal.getFirstIndex();
  }

  private long unsafeSeekToLast() {
    replaceCurrentSegment(journal.getLastSegment());
    unsafeSeek(journal.getLastIndex());

    return journal.getLastIndex();
  }

  /** Rewinds the journal to the given index. */
  private void rewind(final long index) {
    if (currentSegment.index() >= index) {
      final long lookupIndex = index == Long.MIN_VALUE ? index : index - 1; // avoid underflow
      final Segment segment = journal.getSegment(lookupIndex);
      if (segment != null) {
        replaceCurrentSegment(segment);
      }
    }

    currentReader.seek(index);
  }

  /** Fast forwards the journal to the given index. */
  private void forward(final long index) {
    // skip to the correct segment if there is one
    if (!currentSegment.equals(journal.getLastSegment())) {
      final Segment segment = journal.getSegment(index);
      if (segment != null && !segment.equals(currentSegment)) {
        replaceCurrentSegment(segment);
      }
    }

    currentReader.seek(index);
  }

  private boolean unsafeHasNext() {
    if (!currentReader.hasNext()) {
      if (!currentSegment.isOpen()) {
        // When the segment has been deleted concurrently, we do not want to allow the readers to
        // read further until the reader is reset.
        return false;
      }

      final Segment nextSegment = journal.getNextSegment(currentSegment.index());
      if (nextSegment != null && nextSegment.index() == getNextIndex()) {
        replaceCurrentSegment(nextSegment);
        return currentReader.hasNext();
      }
      return false;
    }
    return true;
  }

  private void replaceCurrentSegment(final Segment nextSegment) {
    if (currentSegment.equals(nextSegment)) {
      currentReader.reset();
      return;
    }

    currentReader.close();
    currentSegment = nextSegment;
    currentReader = currentSegment.createReader();
  }
}
