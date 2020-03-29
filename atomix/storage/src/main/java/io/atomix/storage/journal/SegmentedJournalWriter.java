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
package io.atomix.storage.journal;

import io.atomix.storage.statistics.JournalMetrics;
import java.nio.BufferOverflowException;

/** Raft log writer. */
public class SegmentedJournalWriter<E> implements JournalWriter<E> {
  private final SegmentedJournal<E> journal;
  private final JournalMetrics journalMetrics;
  private JournalSegment<E> currentSegment;
  private MappableJournalSegmentWriter<E> currentWriter;

  public SegmentedJournalWriter(final SegmentedJournal<E> journal) {
    this.journal = journal;
    journalMetrics = journal.getJournalMetrics();
    this.currentSegment = journal.getLastSegment();
    currentSegment.acquire();
    this.currentWriter = currentSegment.writer();
  }

  @Override
  public long getLastIndex() {
    return currentWriter.getLastIndex();
  }

  @Override
  public Indexed<E> getLastEntry() {
    return currentWriter.getLastEntry();
  }

  @Override
  public long getNextIndex() {
    return currentWriter.getNextIndex();
  }

  @Override
  public <T extends E> Indexed<T> append(final T entry) {
    try {
      return currentWriter.append(entry);
    } catch (final BufferOverflowException e) {
      if (currentSegment.index() == currentWriter.getNextIndex()) {
        throw e;
      }

      journalMetrics.observeSegmentCreation(this::createNewSegment);

      return currentWriter.append(entry);
    }
  }

  @Override
  public void append(final Indexed<E> entry) {
    try {
      currentWriter.append(entry);
    } catch (final BufferOverflowException e) {
      if (currentSegment.index() == currentWriter.getNextIndex()) {
        throw e;
      }
      journalMetrics.observeSegmentCreation(this::createNewSegment);

      currentWriter.append(entry);
    }
  }

  @Override
  public void commit(final long index) {
    if (index > journal.getCommitIndex()) {
      journal.setCommitIndex(index);
      if (journal.isFlushOnCommit()) {
        flush();
      }
    }
  }

  @Override
  public void reset(final long index) {
    if (index > currentSegment.index()) {
      currentSegment.release();
      currentSegment = journal.resetSegments(index);
      currentSegment.acquire();
      currentWriter = currentSegment.writer();
    } else {
      truncate(index - 1);
    }
    journal.resetHead(index);
  }

  @Override
  public void truncate(final long index) {
    if (index < journal.getCommitIndex()) {
      throw new IndexOutOfBoundsException("Cannot truncate committed index: " + index);
    }

    journalMetrics.observeSegmentTruncation(
        () -> {
          // Delete all segments with first indexes greater than the given index.
          while (index < currentSegment.index() && currentSegment != journal.getFirstSegment()) {
            currentSegment.release();
            journal.removeSegment(currentSegment);
            currentSegment = journal.getLastSegment();
            currentSegment.acquire();
            currentWriter = currentSegment.writer();
          }

          // Truncate the current index.
          currentWriter.truncate(index);

          // Reset segment readers.
          journal.resetTail(index + 1);
        });
  }

  @Override
  public void flush() {
    journalMetrics.observeSegmentFlush(currentWriter::flush);
  }

  @Override
  public void close() {
    currentWriter.close();
  }

  private void createNewSegment() {
    currentWriter.flush();
    currentSegment.release();
    currentSegment = journal.getNextSegment();
    currentSegment.acquire();
    currentWriter = currentSegment.writer();
  }
}
