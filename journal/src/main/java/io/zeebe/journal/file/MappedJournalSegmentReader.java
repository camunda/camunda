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
  private long currentIndex;
  private final JournalRecordReaderUtil recordReader;
  private final int descriptorLength;

  MappedJournalSegmentReader(
      final ByteBuffer buffer, final JournalSegment segment, final JournalIndex index) {
    this.index = index;
    this.segment = segment;
    descriptorLength = segment.descriptor().length();
    recordReader = new JournalRecordReaderUtil(new SBESerializer());
    this.buffer = buffer;
    reset();
  }

  public boolean hasNext() {
    // if the next entry exists the version would be non-zero
    return FrameUtil.hasValidVersion(buffer);
  }

  public JournalRecord next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    // Read version so that buffer's position is advanced.
    FrameUtil.readVersion(buffer);

    final var currentEntry = recordReader.read(buffer, getNextIndex());
    // currentEntry should not be null as hasNext returns true
    currentIndex = currentEntry.index();
    return currentEntry;
  }

  public void reset() {
    buffer.position(descriptorLength);
    currentIndex = segment.index() - 1;
  }

  public void seek(final long index) {
    final long firstIndex = segment.index();
    final long lastIndex = segment.lastIndex();

    reset();

    final var position = this.index.lookup(index - 1);
    if (position != null && position.index() >= firstIndex && position.index() <= lastIndex) {
      buffer.position(position.position());
      currentIndex = position.index() - 1;
    }

    while (getNextIndex() < index && hasNext()) {
      next();
    }
  }

  public void close() {
    segment.onReaderClosed(this);
  }

  long getNextIndex() {
    return currentIndex + 1;
  }
}
