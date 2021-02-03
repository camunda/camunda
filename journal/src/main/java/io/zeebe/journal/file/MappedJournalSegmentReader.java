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

import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.zeebe.journal.JournalRecord;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.NoSuchElementException;
import java.util.zip.CRC32;
import org.agrona.DirectBuffer;
import org.agrona.IoUtil;
import org.agrona.concurrent.UnsafeBuffer;

/** Log segment reader. */
class MappedJournalSegmentReader {

  private static final Namespace NAMESPACE =
      new Namespace.Builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
          .register(PersistedJournalRecord.class)
          .register(UnsafeBuffer.class)
          .name("Journal")
          .build();
  private final MappedByteBuffer buffer;
  private final int maxEntrySize;
  private final JournalIndex index;
  private final JournalSegment segment;
  private JournalRecord currentEntry;
  private JournalRecord nextEntry;
  private final CRC32 crc32 = new CRC32();

  MappedJournalSegmentReader(
      final JournalSegmentFile file,
      final JournalSegment segment,
      final int maxEntrySize,
      final JournalIndex index) {
    this.maxEntrySize = maxEntrySize;
    this.index = index;
    this.segment = segment;
    buffer =
        IoUtil.mapExistingFile(
            file.file(), MapMode.READ_ONLY, file.name(), 0, segment.descriptor().maxSegmentSize());
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
    IoUtil.unmap(buffer);
    segment.onReaderClosed(this);
  }

  long getNextIndex() {
    return currentEntry == null ? segment.index() : currentEntry.index() + 1;
  }

  /** Reads the next entry in the segment. */
  private void readNext(final long expectedIndex) {

    // Mark the buffer so it can be reset if necessary.
    buffer.mark();

    try {
      // Read the length of the record.
      final int length = buffer.getInt();

      // If the buffer length is zero then return.
      if (length <= 0 || length > maxEntrySize) {
        buffer.reset();
        nextEntry = null;
        return;
      }

      final ByteBuffer slice = buffer.slice();
      slice.limit(length);

      // If the stored checksum equals the computed checksum, return the record.
      slice.rewind();
      final PersistedJournalRecord record = NAMESPACE.deserialize(slice);
      final var checksum = record.checksum();
      // TODO: checksum should also include asqn.
      // TODO: It is now copying the data to calculate the checksum. This should be fixed.
      final var expectedChecksum = computeChecksum(record.data());
      if (checksum != expectedChecksum || expectedIndex != record.index()) {
        nextEntry = null;
        buffer.reset();
        return;
      }
      nextEntry = record;
      buffer.position(buffer.position() + length);

    } catch (final BufferUnderflowException e) {
      buffer.reset();
      nextEntry = null;
    }
  }

  private int computeChecksum(final DirectBuffer data) {
    final byte[] slice = new byte[data.capacity()];
    data.getBytes(0, slice);
    crc32.reset();
    crc32.update(slice);
    return (int) crc32.getValue();
  }

  long getCurrentIndex() {
    return currentEntry != null ? currentEntry.index() : 0;
  }
}
