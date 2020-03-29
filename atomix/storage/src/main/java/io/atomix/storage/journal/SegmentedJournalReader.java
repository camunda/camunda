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

import java.util.NoSuchElementException;

/** Raft log reader. */
public class SegmentedJournalReader<E> implements JournalReader<E> {

  private final SegmentedJournal<E> journal;
  private final Mode mode;
  private JournalSegment<E> currentSegment;
  private Indexed<E> previousEntry;
  private MappableJournalSegmentReader<E> currentReader;

  SegmentedJournalReader(final SegmentedJournal<E> journal, final long index, final Mode mode) {
    this.journal = journal;
    this.mode = mode;
    initialize(index);
  }

  /** Initializes the reader to the given index. */
  private void initialize(final long index) {
    currentSegment = journal.getSegment(index);
    currentSegment.acquire();
    currentReader = currentSegment.createReader();
    long nextIndex = getNextIndex();
    while (index > nextIndex && hasNext()) {
      next();
      nextIndex = getNextIndex();
    }
  }

  @Override
  public boolean isEmpty() {
    final JournalSegment<E> firstSegment = journal.getFirstSegment();
    if (firstSegment.isEmpty()) {
      return true;
    }

    return mode == Mode.COMMITS && journal.getCommitIndex() < firstSegment.index();
  }

  @Override
  public long getFirstIndex() {
    return journal.getFirstSegment().index();
  }

  @Override
  public long getLastIndex() {
    return mode == Mode.COMMITS ? journal.getCommitIndex() : journal.getLastSegment().lastIndex();
  }

  @Override
  public long getCurrentIndex() {
    final long currentIndex = currentReader.getCurrentIndex();
    if (currentIndex != 0) {
      return currentIndex;
    }
    if (previousEntry != null) {
      return previousEntry.index();
    }
    return 0;
  }

  @Override
  public Indexed<E> getCurrentEntry() {
    final Indexed<E> currentEntry = currentReader.getCurrentEntry();
    if (currentEntry != null) {
      return currentEntry;
    }
    return previousEntry;
  }

  @Override
  public long getNextIndex() {
    return currentReader.getNextIndex();
  }

  @Override
  public boolean hasNext() {
    if (mode == Mode.ALL) {
      return hasNextEntry();
    }

    final long nextIndex = getNextIndex();
    final long commitIndex = journal.getCommitIndex();
    return nextIndex <= commitIndex && hasNextEntry();
  }

  @Override
  public Indexed<E> next() {
    if (!currentReader.hasNext()) {
      final JournalSegment<E> nextSegment = journal.getNextSegment(currentSegment.index());
      if (nextSegment != null && nextSegment.index() == getNextIndex()) {
        previousEntry = currentReader.getCurrentEntry();
        replaceCurrentSegment(nextSegment);
        return currentReader.next();
      } else {
        throw new NoSuchElementException();
      }
    } else {
      previousEntry = currentReader.getCurrentEntry();
      return currentReader.next();
    }
  }

  @Override
  public void reset() {
    replaceCurrentSegment(journal.getFirstSegment());
    previousEntry = null;
  }

  @Override
  public void reset(final long index) {
    // If the current segment is not open, it has been replaced. Reset the segments.
    if (!currentSegment.isOpen()) {
      reset();
    }

    if (index < currentReader.getNextIndex()) {
      rewind(index);
    } else if (index > currentReader.getNextIndex()) {
      forward(index);
    } else {
      currentReader.reset(index);
    }
  }

  @Override
  public void close() {
    currentReader.close();
    journal.closeReader(this);
  }

  /** Rewinds the journal to the given index. */
  private void rewind(final long index) {
    if (currentSegment.index() >= index) {
      final JournalSegment<E> segment = journal.getSegment(index - 1);
      if (segment != null) {
        replaceCurrentSegment(segment);
      }
    }

    currentReader.reset(index);
    previousEntry = currentReader.getCurrentEntry();
  }

  /** Fast forwards the journal to the given index. */
  private void forward(final long index) {
    // skip to the correct segment if there is one
    if (!currentSegment.equals(journal.getLastSegment())) {
      final JournalSegment<E> segment = journal.getSegment(index);
      if (segment != null && !segment.equals(currentSegment)) {
        replaceCurrentSegment(segment);
      }
    }

    while (getNextIndex() < index && hasNext()) {
      next();
    }
  }

  private boolean hasNextEntry() {
    if (!currentReader.hasNext()) {
      final JournalSegment<E> nextSegment = journal.getNextSegment(currentSegment.index());
      if (nextSegment != null && nextSegment.index() == getNextIndex()) {
        previousEntry = currentReader.getCurrentEntry();
        replaceCurrentSegment(nextSegment);
        return currentReader.hasNext();
      }
      return false;
    }
    return true;
  }

  private void replaceCurrentSegment(final JournalSegment<E> nextSegment) {
    currentReader.close();
    currentSegment.release();
    currentSegment = nextSegment;
    currentSegment.acquire();
    currentReader = currentSegment.createReader();
  }
}
