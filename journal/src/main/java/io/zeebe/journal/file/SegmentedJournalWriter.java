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
package io.zeebe.journal.file;

import io.zeebe.journal.JournalRecord;
import java.nio.BufferOverflowException;
import org.agrona.DirectBuffer;

class SegmentedJournalWriter {
  private final SegmentedJournal journal;
  private final JournalMetrics journalMetrics;
  private JournalSegment currentSegment;
  private MappedJournalSegmentWriter currentWriter;

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

  public JournalRecord append(final long asqn, final DirectBuffer data) {
    try {
      return currentWriter.append(asqn, data);
    } catch (final BufferOverflowException e) {
      if (currentSegment.index() == currentWriter.getNextIndex()) {
        throw e;
      }

      journalMetrics.observeSegmentCreation(this::createNewSegment);

      return currentWriter.append(asqn, data);
    }
  }

  public void append(final JournalRecord record) {
    try {
      currentWriter.append(record);
    } catch (final BufferOverflowException e) {
      if (currentSegment.index() == currentWriter.getNextIndex()) {
        throw e;
      }

      journalMetrics.observeSegmentCreation(this::createNewSegment);
      currentWriter.append(record);
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
