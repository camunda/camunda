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
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.function.Function;

final class SegmentedJournalWriter {
  private final SegmentsManager segments;
  private final SegmentsFlusher flusher;
  private final JournalMetrics journalMetrics;

  private Segment currentSegment;
  private SegmentWriter currentWriter;

  SegmentedJournalWriter(
      final SegmentsManager segments,
      final SegmentsFlusher flusher,
      final JournalMetrics journalMetrics) {
    this.segments = segments;
    this.flusher = flusher;
    this.journalMetrics = journalMetrics;

    currentSegment = segments.getLastSegment();
    currentWriter = currentSegment.writer();
  }

  long getLastIndex() {
    return currentWriter.getLastIndex();
  }

  long getNextIndex() {
    return currentWriter.getNextIndex();
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
    flusher.setLastFlushedIndex(index - 1);
    currentSegment = segments.resetSegments(index);
    currentWriter = currentSegment.writer();
  }

  void deleteAfter(final long index) {
    // reset the last flushed index first to avoid corruption on restart in case of partial
    // truncation (e.g. the node crashed while deleting segments)
    flusher.setLastFlushedIndex(index);

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
    flusher.flush(segments.getTailSegments(flusher.nextFlushIndex()).values());
  }

  void createNewSegment() {
    currentSegment.updateDescriptor();
    currentSegment = segments.getNextSegment();
    currentWriter = currentSegment.writer();
  }
}
