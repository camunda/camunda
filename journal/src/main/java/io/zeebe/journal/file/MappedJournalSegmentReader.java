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
import io.zeebe.journal.file.record.JournalRecordReaderUtil;
import io.zeebe.journal.file.record.SBESerializer;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;

/** Log segment reader. */
class MappedJournalSegmentReader {

  private final ByteBuffer buffer;
  private final JournalIndex index;
  private final JournalSegment segment;
  private JournalRecord currentEntry;
  private JournalRecord nextEntry;
  private final JournalRecordReaderUtil recordReader;

  MappedJournalSegmentReader(
      final ByteBuffer buffer, final JournalSegment segment, final JournalIndex index) {
    this.index = index;
    this.segment = segment;
    recordReader = new JournalRecordReaderUtil(new SBESerializer());
    this.buffer = buffer;
    reset();
  }

  public JournalRecord getCurrentEntry() {
    return currentEntry;
  }

  public JournalRecord getNextEntry() {
    return nextEntry;
  }

  public boolean hasNext() {
    // If the next entry is null, check whether a next entry exists.
    if (nextEntry == null) {
      readNext(getNextIndex());
    }
    return nextEntry != null;
  }

  public JournalRecord next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    // Set the current entry to the next entry.
    currentEntry = nextEntry;

    // Reset the next entry to null.
    nextEntry = null;

    // Read the next entry in the segment.
    readNext(getNextIndex());

    // Return the current entry.
    return currentEntry;
  }

  public void reset() {
    buffer.position(JournalSegmentDescriptor.BYTES);
    currentEntry = null;
    nextEntry = null;
    readNext(getNextIndex());
  }

  public boolean seek(final long index) {
    final long firstIndex = segment.index();
    final long lastIndex = segment.lastIndex();

    reset();

    final var position = this.index.lookup(index - 1);
    if (position != null && position.index() >= firstIndex && position.index() <= lastIndex) {
      currentEntry = null;
      buffer.position(position.position());

      nextEntry = null;
      readNext(position.index());
    }

    while (getNextIndex() < index && hasNext()) {
      next();
    }

    return nextEntry != null && nextEntry.index() == index;
  }

  public void close() {
    segment.onReaderClosed(this);
  }

  long getNextIndex() {
    return currentEntry == null ? segment.index() : currentEntry.index() + 1;
  }

  /** Reads the next entry in the segment. */
  private void readNext(final long expectedIndex) {
    nextEntry = recordReader.read(buffer, expectedIndex);
  }

  long getCurrentIndex() {
    return currentEntry != null ? currentEntry.index() : 0;
  }
}
