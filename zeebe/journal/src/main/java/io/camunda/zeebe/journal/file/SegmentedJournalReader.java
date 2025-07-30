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
import java.util.NoSuchElementException;

class SegmentedJournalReader implements JournalReader {

  private final SegmentedJournal journal;
  private Segment currentSegment;
  private SegmentReader currentReader;
  private final JournalMetrics metrics;

  SegmentedJournalReader(final SegmentedJournal journal, final JournalMetrics journalMetrics) {
    this.journal = journal;
    metrics = journalMetrics;
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
  public long getNextIndex() {
    return currentReader.getNextIndex();
  }

  @Override
  public void close() {
    currentReader.close();
    journal.closeReader(this);
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
