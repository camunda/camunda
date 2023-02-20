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

import io.camunda.zeebe.journal.JournalException.SegmentSizeTooSmall;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SegmentedJournalWriter {
  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentedJournalWriter.class);
  private final SegmentsManager segments;
  private final JournalMetrics journalMetrics;

  private Segment currentSegment;
  private SegmentWriter currentWriter;

  // no need to make this volatile if callers always acquire the write-lock beforehand
  private long lastFlushedIndex;

  SegmentedJournalWriter(
      final SegmentsManager segments,
      final long lastFlushedIndex,
      final JournalMetrics journalMetrics) {
    this.segments = segments;
    this.lastFlushedIndex = lastFlushedIndex;
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
    final var appendResult = currentWriter.append(asqn, recordDataWriter);
    if (appendResult.isRight()) {
      return appendResult.get();
    }

    if (currentSegment.index() == currentWriter.getNextIndex()) {
      throw new SegmentSizeTooSmall("Failed appending, segment size is too small");
    }

    journalMetrics.observeSegmentCreation(this::createNewSegment);
    final var appendResultOnNewSegment = currentWriter.append(asqn, recordDataWriter);
    if (appendResultOnNewSegment.isLeft()) {
      throw appendResultOnNewSegment.getLeft();
    }
    return appendResultOnNewSegment.get();
  }

  void append(final JournalRecord record) {
    final var appendResult = currentWriter.append(record);
    if (appendResult.isRight()) {
      return;
    }

    if (currentSegment.index() == currentWriter.getNextIndex()) {
      throw new SegmentSizeTooSmall("Failed appending, segment size is too small");
    }

    journalMetrics.observeSegmentCreation(this::createNewSegment);
    final var resultInNewSegment = currentWriter.append(record);
    if (resultInNewSegment.isLeft()) {
      throw resultInNewSegment.getLeft();
    }
  }

  void reset(final long index) {
    currentSegment = segments.resetSegments(index);
    currentWriter = currentSegment.writer();
    lastFlushedIndex = index - 1;
  }

  void deleteAfter(final long index) {
    // Delete all segments with first indexes greater than the given index.
    while (index < currentSegment.index() && currentSegment != segments.getFirstSegment()) {
      segments.removeSegment(currentSegment);
      currentSegment = segments.getLastSegment();
      currentWriter = currentSegment.writer();
    }

    // Truncate the current index.
    currentWriter.truncate(index);
    lastFlushedIndex = index - 1;
  }

  /**
   * Fetches all segments with a last index greater than or equal to current {@link
   * #lastFlushedIndex}. These are then flushed in order. The {@link Segment#lastIndex()} of the
   * last successful segment to be flushed will be stored in the given {@link JournalMetaStore}.
   *
   * @param metaStore where to store the last successfully flushed index
   */
  void flush(final JournalMetaStore metaStore) {
    final var dirtySegments = segments.getSegments(lastFlushedIndex + 1);
    final var segmentsCount = dirtySegments.size();
    long flushedIndex = -1;

    if (segmentsCount == 0) {
      LOGGER.debug(
          "No segments to flush for index {} (last index = {}); there may be nothing to flush",
          flushedIndex,
          getLastIndex());
      return;
    }

    try {
      for (final var segment : dirtySegments) {
        final long lastSegmentIndex = segment.lastIndex();
        if (segment.flush()) {
          flushedIndex = lastSegmentIndex;
        }
      }
    } finally {
      // store whatever we managed to flush to avoid doing it again
      if (flushedIndex > lastFlushedIndex) {
        lastFlushedIndex = flushedIndex;
        metaStore.storeLastFlushedIndex(flushedIndex);

        LOGGER.trace(
            "Flushed {} segment(s), from index {} to index {}",
            segmentsCount,
            lastFlushedIndex,
            flushedIndex);
      }
    }
  }

  private void createNewSegment() {
    currentSegment = segments.getNextSegment();
    currentWriter = currentSegment.writer();
  }
}
