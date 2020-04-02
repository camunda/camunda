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

import com.esotericsoftware.kryo.KryoException;
import io.atomix.storage.StorageException;
import io.atomix.storage.journal.index.JournalIndex;
import io.atomix.utils.memory.BufferCleaner;
import io.atomix.utils.serializer.Namespace;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.zip.CRC32;

/**
 * Segment writer.
 *
 * <p>The format of an entry in the log is as follows:
 *
 * <ul>
 *   <li>64-bit index
 *   <li>8-bit boolean indicating whether a term change is contained in the entry
 *   <li>64-bit optional term
 *   <li>32-bit signed entry length, including the entry type ID
 *   <li>8-bit signed entry type ID
 *   <li>n-bit entry bytes
 * </ul>
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
class MappedJournalSegmentWriter<E> implements JournalWriter<E> {

  private final MappedByteBuffer mappedBuffer;
  private final ByteBuffer buffer;
  private final JournalSegment<E> segment;
  private final int maxEntrySize;
  private final JournalIndex index;
  private final Namespace namespace;
  private final long firstIndex;
  private Indexed<E> lastEntry;

  MappedJournalSegmentWriter(
      final MappedByteBuffer buffer,
      final JournalSegment<E> segment,
      final int maxEntrySize,
      final JournalIndex index,
      final Namespace namespace) {
    this.mappedBuffer = buffer;
    this.buffer = buffer.slice();
    this.segment = segment;
    this.maxEntrySize = maxEntrySize;
    this.index = index;
    this.namespace = namespace;
    this.firstIndex = segment.index();
    reset(0);
  }

  /**
   * Returns the mapped buffer underlying the segment writer.
   *
   * @return the mapped buffer underlying the segment writer
   */
  MappedByteBuffer buffer() {
    return mappedBuffer;
  }

  @Override
  public long getLastIndex() {
    return lastEntry != null ? lastEntry.index() : segment.index() - 1;
  }

  @Override
  public Indexed<E> getLastEntry() {
    return lastEntry;
  }

  @Override
  public long getNextIndex() {
    if (lastEntry != null) {
      return lastEntry.index() + 1;
    } else {
      return firstIndex;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends E> Indexed<T> append(final T entry) {
    // Store the entry index.
    final long index = getNextIndex();

    // Serialize the entry.
    final int position = buffer.position();
    if (position + Integer.BYTES + Integer.BYTES > buffer.limit()) {
      throw new BufferOverflowException();
    }

    buffer.position(position + Integer.BYTES + Integer.BYTES);

    try {
      namespace.serialize(entry, buffer);
    } catch (final KryoException e) {
      throw new BufferOverflowException();
    }

    final int length = buffer.position() - (position + Integer.BYTES + Integer.BYTES);

    // If the entry length exceeds the maximum entry size then throw an exception.
    if (length > maxEntrySize) {
      // Just reset the buffer. There's no need to zero the bytes since we haven't written the
      // length or checksum.
      buffer.position(position);
      throw new StorageException.TooLarge(
          "Entry size " + length + " exceeds maximum allowed bytes (" + maxEntrySize + ")");
    }

    // Compute the checksum for the entry.
    final CRC32 crc32 = new CRC32();
    buffer.position(position + Integer.BYTES + Integer.BYTES);
    final ByteBuffer slice = buffer.slice();
    slice.limit(length);
    crc32.update(slice);
    final long checksum = crc32.getValue();

    // Create a single byte[] in memory for the entire entry and write it as a batch to the
    // underlying buffer.
    buffer.position(position);
    buffer.putInt(length);
    buffer.putInt((int) checksum);
    buffer.position(position + Integer.BYTES + Integer.BYTES + length);

    // Update the last entry with the correct index/term/length.
    final Indexed<E> indexedEntry = new Indexed<>(index, entry, length);
    this.lastEntry = indexedEntry;
    this.index.index(lastEntry, position);
    return (Indexed<T>) indexedEntry;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void append(final Indexed<E> entry) {
    final long nextIndex = getNextIndex();

    // If the entry's index is greater than the next index in the segment, skip some entries.
    if (entry.index() > nextIndex) {
      throw new IndexOutOfBoundsException("Entry index is not sequential");
    }

    // If the entry's index is less than the next index, truncate the segment.
    if (entry.index() < nextIndex) {
      truncate(entry.index() - 1);
    }
    append(entry.entry());
  }

  @Override
  public void commit(final long index) {}

  @Override
  public void reset(final long index) {
    long nextIndex = firstIndex;

    // Clear the buffer indexes.
    buffer.position(JournalSegmentDescriptor.BYTES);

    // Record the current buffer position.
    int position = buffer.position();

    // Read the entry length.
    buffer.mark();

    try {
      int length = buffer.getInt();

      // If the length is non-zero, read the entry.
      while (0 < length && length <= maxEntrySize && (index == 0 || nextIndex <= index)) {

        // Read the checksum of the entry.
        final long checksum = buffer.getInt() & 0xFFFFFFFFL;

        // Compute the checksum for the entry bytes.
        final CRC32 crc32 = new CRC32();
        final ByteBuffer slice = buffer.slice();
        slice.limit(length);
        crc32.update(slice);

        // If the stored checksum equals the computed checksum, return the entry.
        if (checksum == crc32.getValue()) {
          slice.rewind();
          final E entry = namespace.deserialize(slice);
          lastEntry = new Indexed<>(nextIndex, entry, length);
          this.index.index(lastEntry, position);
          nextIndex++;
        } else {
          break;
        }

        // Update the current position for indexing.
        position = buffer.position() + length;
        buffer.position(position);

        buffer.mark();
        length = buffer.getInt();
      }

      // Reset the buffer to the previous mark.
      buffer.reset();
    } catch (final BufferUnderflowException e) {
      buffer.reset();
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void truncate(final long index) {
    // If the index is greater than or equal to the last index, skip the truncate.
    if (index >= getLastIndex()) {
      return;
    }

    // Reset the last entry.
    lastEntry = null;

    // Truncate the index.
    this.index.truncate(index);

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

  @Override
  public void flush() {
    mappedBuffer.force();
  }

  @Override
  public void close() {
    flush();
    try {
      BufferCleaner.freeBuffer(mappedBuffer);
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  /**
   * Returns the size of the underlying buffer.
   *
   * @return The size of the underlying buffer.
   */
  public long size() {
    return buffer.position() + JournalSegmentDescriptor.BYTES;
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
