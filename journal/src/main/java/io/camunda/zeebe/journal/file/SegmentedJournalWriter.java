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
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.util.buffer.BufferWriter;

class SegmentedJournalWriter {
  private final SegmentedJournal journal;
  private final JournalMetrics journalMetrics;
  private Segment currentSegment;
  private SegmentWriter currentWriter;

  public SegmentedJournalWriter(final SegmentedJournal journal) {
    this.journal = journal;
    journalMetrics = journal.getJournalMetrics();
    currentSegment = journal.getLastSegment();
    currentWriter = currentSegment.writer();
  }

  public long getLastIndex() {
    return currentWriter.getLastIndex();
  }

  public JournalRecord getLastEntry() {
    return currentWriter.getLastEntry();
  }

  public long getNextIndex() {
    return currentWriter.getNextIndex();
  }

  public JournalRecord append(final long asqn, final BufferWriter recordDataWriter) {
    final var appendResult = currentWriter.append(asqn, recordDataWriter, journalMetrics);
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

  public void append(final JournalRecord record) {
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

  public void reset(final long index) {
    currentSegment = journal.resetSegments(index);
    currentWriter = currentSegment.writer();
  }

  public void deleteAfter(final long index) {
    // Delete all segments with first indexes greater than the given index.
    while (index < currentSegment.index() && currentSegment != journal.getFirstSegment()) {
      journal.removeSegment(currentSegment);
      currentSegment = journal.getLastSegment();
      currentWriter = currentSegment.writer();
    }

    // Truncate the current index.
    currentWriter.truncate(index);
  }

  public void flush() {
    journalMetrics.observeSegmentFlush(currentWriter::flush);
  }

  public void close() {
    currentWriter.close();
  }

  private void createNewSegment() {
    currentWriter.flush();
    currentSegment = journal.getNextSegment();
    currentWriter = currentSegment.writer();
  }
}
