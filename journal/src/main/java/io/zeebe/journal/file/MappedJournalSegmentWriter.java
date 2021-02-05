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

import com.esotericsoftware.kryo.KryoException;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.zeebe.journal.JournalRecord;
import io.zeebe.journal.StorageException;
import io.zeebe.journal.StorageException.InvalidChecksum;
import io.zeebe.journal.StorageException.InvalidIndex;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.zip.CRC32;
import org.agrona.DirectBuffer;
import org.agrona.IoUtil;
import org.agrona.concurrent.UnsafeBuffer;

/** Segment writer. */
class MappedJournalSegmentWriter {

  private static final Namespace NAMESPACE =
      new Namespace.Builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
          .register(PersistedJournalRecord.class)
          .register(UnsafeBuffer.class)
          .name("Journal")
          .build();
  private final MappedByteBuffer buffer;
  private final JournalSegment segment;
  private final int maxEntrySize;
  private final JournalIndex index;
  private final long firstIndex;
  private final CRC32 crc32 = new CRC32();
  private JournalRecord lastEntry;
  private boolean isOpen = true;

  MappedJournalSegmentWriter(
      final JournalSegmentFile file,
      final JournalSegment segment,
      final int maxEntrySize,
      final JournalIndex index) {
    this.segment = segment;
    this.maxEntrySize = maxEntrySize;
    this.index = index;
    firstIndex = segment.index();
    buffer = mapFile(file, segment);
    reset(0);
  }

  private static MappedByteBuffer mapFile(
      final JournalSegmentFile file, final JournalSegment segment) {
    // map existing file, because file is already created by SegmentedJournal
    return IoUtil.mapExistingFile(
        file.file(), file.name(), 0, segment.descriptor().maxSegmentSize());
  }

  public long getLastIndex() {
    return lastEntry != null ? lastEntry.index() : segment.index() - 1;
  }

  public JournalRecord getLastEntry() {
    return lastEntry;
  }

  public long getNextIndex() {
    if (lastEntry != null) {
      return lastEntry.index() + 1;
    } else {
      return firstIndex;
    }
  }

  public JournalRecord append(final long asqn, final DirectBuffer data) {
    // Store the entry index.
    final long index = getNextIndex();

    // TODO: Should reject append if the asqn is not greater than the previous record

    // Serialize the entry.
    final int recordStartPosition = buffer.position();
    if (recordStartPosition + Integer.BYTES > buffer.limit()) {
      throw new BufferOverflowException();
    }

    buffer.position(recordStartPosition + Integer.BYTES);

    // compute checksum and construct the record
    // TODO: checksum should also include asqn. https://github.com/zeebe-io/zeebe/issues/6218
    // TODO: It is now copying the data to calculate the checksum. This should be fixed when
    // we change the serialization format. https://github.com/zeebe-io/zeebe/issues/6219
    final var checksum = computeChecksum(data);
    final var recordToWrite = new PersistedJournalRecord(index, asqn, checksum, data);

    try {
      NAMESPACE.serialize(recordToWrite, buffer);
    } catch (final KryoException e) {
      throw new BufferOverflowException();
    }

    final int length = buffer.position() - (recordStartPosition + Integer.BYTES);

    // If the entry length exceeds the maximum entry size then throw an exception.
    if (length > maxEntrySize) {
      // Just reset the buffer. There's no need to zero the bytes since we haven't written the
      // length or checksum.
      buffer.position(recordStartPosition);
      throw new StorageException.TooLarge(
          "Entry size " + length + " exceeds maximum allowed bytes (" + maxEntrySize + ")");
    }

    buffer.position(recordStartPosition);
    buffer.putInt(length);
    buffer.position(recordStartPosition + Integer.BYTES + length);

    lastEntry = recordToWrite;
    this.index.index(lastEntry, recordStartPosition);
    return lastEntry;
  }

  private int computeChecksum(final DirectBuffer data) {
    final byte[] slice = new byte[data.capacity()];
    data.getBytes(0, slice);
    crc32.reset();
    crc32.update(slice);
    return (int) crc32.getValue();
  }

  public void append(final JournalRecord record) {
    final long nextIndex = getNextIndex();

    // If the entry's index is not the expected next index in the segment, fail the append.
    if (record.index() != nextIndex) {
      throw new InvalidIndex(
          String.format(
              "The record index is not sequential. Expected the next index to be %d, but the record to append has index %d",
              nextIndex, record.index()));
    }

    final int recordStartPosition = buffer.position();
    if (recordStartPosition + Integer.BYTES > buffer.limit()) {
      throw new BufferOverflowException();
    }

    buffer.position(recordStartPosition + Integer.BYTES);
    final var checksum = computeChecksum(record.data());

    if (checksum != record.checksum()) {
      throw new InvalidChecksum("Checksum invalid for record " + record);
    }
    try {
      NAMESPACE.serialize(record, buffer);
    } catch (final KryoException e) {
      throw new BufferOverflowException();
    }

    final int length = buffer.position() - (recordStartPosition + Integer.BYTES);

    // If the entry length exceeds the maximum entry size then throw an exception.
    if (length > maxEntrySize) {
      // Just reset the buffer. There's no need to zero the bytes since we haven't written the
      // length or checksum.
      buffer.position(recordStartPosition);
      throw new StorageException.TooLarge(
          "Entry size " + length + " exceeds maximum allowed bytes (" + maxEntrySize + ")");
    }

    buffer.position(recordStartPosition);
    buffer.putInt(length);
    buffer.position(recordStartPosition + Integer.BYTES + length);

    lastEntry = record;
    index.index(lastEntry, recordStartPosition);
  }

  private void reset(final long index) {
    long nextIndex = firstIndex;

    // Clear the buffer indexes.
    buffer.position(JournalSegmentDescriptor.BYTES);

    // Read the entry length.
    buffer.mark();

    try {
      var recordPosition = buffer.position();
      int length = buffer.getInt();

      // If the length is non-zero, read the entry.
      while (length > 0 && length <= maxEntrySize && (index == 0 || nextIndex <= index)) {

        final ByteBuffer slice = buffer.slice();
        slice.limit(length);

        // If the stored checksum equals the computed checksum, return the record.
        slice.rewind();
        final PersistedJournalRecord record = NAMESPACE.deserialize(slice);
        final var checksum = record.checksum();
        final var expectedChecksum = computeChecksum(record.data());
        if (checksum != expectedChecksum || nextIndex != record.index()) {
          buffer.reset();
          return;
        }
        lastEntry = record;
        this.index.index(record, recordPosition);
        nextIndex++;
        buffer.position(recordPosition + Integer.BYTES + length);

        recordPosition = buffer.position();
        buffer.mark();
        length = buffer.getInt();
      }

    } catch (final BufferUnderflowException e) {
      // Reached end of the segment
    } finally {
      buffer.reset();
    }
  }

  public void truncate(final long index) {
    // If the index is greater than or equal to the last index, skip the truncate.
    if (index >= getLastIndex()) {
      return;
    }

    // Reset the last entry.
    lastEntry = null;

    // Truncate the index.
    this.index.deleteAfter(index);

    if (index < segment.index()) {
      buffer.position(JournalSegmentDescriptor.BYTES);
      buffer.putInt(0);
      buffer.putInt(0);
      buffer.position(JournalSegmentDescriptor.BYTES);
    } else {
      // Reset the writer to the given index.
      reset(index);

      // Zero entries after the given index.
      final int position = buffer.position();
      buffer.putInt(0);
      buffer.putInt(0);
      buffer.position(position);
    }
  }

  public void flush() {
    buffer.force();
  }

  public void close() {
    if (isOpen) {
      isOpen = false;
      flush();
      IoUtil.unmap(buffer);
    }
  }

  /**
   * Returns a boolean indicating whether the segment is empty.
   *
   * @return Indicates whether the segment is empty.
   */
  public boolean isEmpty() {
    return lastEntry == null;
  }
}
