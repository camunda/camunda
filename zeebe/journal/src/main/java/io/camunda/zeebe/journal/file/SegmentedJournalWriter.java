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

import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalException.SegmentFull;
import io.camunda.zeebe.journal.JournalException.SegmentSizeTooSmall;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SegmentedJournalWriter {
  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentedJournalWriter.class);
  private final SegmentsManager segments;
  private final JournalMetaStore metaStore;
  private final JournalMetrics journalMetrics;
  private long lastFlushedIndex;

  private Segment currentSegment;
  private SegmentWriter currentWriter;

  SegmentedJournalWriter(
      final SegmentsManager segments,
      final JournalMetaStore metaStore,
      final JournalMetrics journalMetrics) {
    this.segments = segments;
    this.metaStore = metaStore;
    this.journalMetrics = journalMetrics;

    lastFlushedIndex = metaStore.loadLastFlushedIndex();
    currentSegment = segments.getLastSegment();
    currentWriter = currentSegment.writer();
  }

  long getLastIndex() {
    return currentWriter.getLastIndex();
  }

  long getNextIndex() {
    return currentWriter.getNextIndex();
  }

  long getLastFlushedIndex() {
    return lastFlushedIndex;
  }

  JournalRecord append(final long asqn, final BufferWriter recordDataWriter) {
    return appendInCurrentSegmentOrNext(
        segmentWriter -> segmentWriter.append(asqn, recordDataWriter));
  }

  void append(final JournalRecord journalRecord) {
    appendInCurrentSegmentOrNext(segmentWriter -> segmentWriter.append(journalRecord));
  }

  JournalRecord append(final long checksum, final byte[] serializedRecord) {
    return appendInCurrentSegmentOrNext(
        segmentWriter -> segmentWriter.append(checksum, serializedRecord));
  }

  /**
   * Tries to append a record using the given inSegmentAppender. If the segment is full, a new
   * segment is created and tries to attempt the record in the new segment.
   *
   * @param inSegmentAppender A method that appends a record in a given segment, returns a journal
   *     record if successfully appended or returns SegmentFull.
   * @return the appended journal record
   */
  private JournalRecord appendInCurrentSegmentOrNext(
      final Function<SegmentWriter, Either<SegmentFull, JournalRecord>> inSegmentAppender) {
    final var appendResult = inSegmentAppender.apply(currentWriter);
    if (appendResult.isRight()) {
      return appendResult.get();
    }

    if (currentSegment.index() == currentWriter.getNextIndex()) {
      throw new SegmentSizeTooSmall("Failed appending, segment size is too small");
    }

    journalMetrics.observeSegmentCreation(this::createNewSegment);
    final var appendResultOnNewSegment = inSegmentAppender.apply(currentWriter);
    if (appendResultOnNewSegment.isLeft()) {
      throw appendResultOnNewSegment.getLeft();
    }
    return appendResultOnNewSegment.get();
  }

  void reset(final long index) {
    lastFlushedIndex = index - 1;
    metaStore.storeLastFlushedIndex(index - 1);
    currentSegment = segments.resetSegments(index);
    currentWriter = currentSegment.writer();
  }

  void deleteAfter(final long index) {
    // reset the last flushed index first to avoid corruption on restart in case of partial
    // truncation (e.g. the node crashed while deleting segments)
    lastFlushedIndex = index;
    metaStore.storeLastFlushedIndex(index);

    // Delete all segments with first indexes greater than the given index.
    while (index < currentSegment.index() && currentSegment != segments.getFirstSegment()) {
      segments.removeSegment(currentSegment);
      currentSegment = segments.getLastSegment();
      currentWriter = currentSegment.writer();
    }

    // Reset last entry position in descriptor to 0, to ensure that after a restart it is not using
    // the old truncated entry.
    currentSegment.resetLastEntryInDescriptor();
    // Truncate down to the current index, such that the last index is `index`, and the next index
    // `index + 1`
    currentWriter.truncate(index);
  }

  void flush() throws FlushException {
    // even if the next flush index has not been written, this will always flush at least the last
    // segment if only to cover cases such as truncating the log, where the next flush index may not
    // have been written yet but we still want to flush that segment after modifying it
    flush(segments.getTailSegments(lastFlushedIndex + 1).values());
  }

  /**
   * Fetches all segments with a last index greater than or equal to current {@link
   * #lastFlushedIndex}. These are then flushed in order. The {@link Segment#lastIndex()} of the
   * last successful segment to be flushed will be stored in the given {@link JournalMetaStore}.
   *
   * @param dirtySegments the list of segments which need to be flushed
   */
  private void flush(final Collection<? extends FlushableSegment> dirtySegments)
      throws FlushException {
    final var segmentsCount = dirtySegments.size();
    long flushedIndex = -1;

    if (segmentsCount == 0) {
      LOGGER.debug(
          "No segments to flush for index {}; there may be nothing to flush", flushedIndex);
    }

    try {
      for (final var segment : dirtySegments) {
        final long lastSegmentIndex = segment.lastIndex();
        segment.flush(); // throws FlushException
        flushedIndex = lastSegmentIndex;
      }
    } finally {
      // store whatever we managed to flush to avoid doing it again
      if (flushedIndex > lastFlushedIndex) {
        lastFlushedIndex = flushedIndex;

        LOGGER.trace(
            "Flushed {} segment(s), from index {} to index {}",
            segmentsCount,
            lastFlushedIndex,
            flushedIndex);
      }
    }
  }

  private void createNewSegment() {
    currentSegment.updateDescriptor();
    currentSegment = segments.getNextSegment();
    currentWriter = currentSegment.writer();
  }
}
