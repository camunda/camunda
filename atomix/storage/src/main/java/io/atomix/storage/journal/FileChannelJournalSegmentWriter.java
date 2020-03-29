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
import io.atomix.utils.serializer.Namespace;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

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
class FileChannelJournalSegmentWriter<E> implements JournalWriter<E> {

  private final FileChannel channel;
  private final JournalSegment segment;
  private final int maxEntrySize;
  private final JournalIndex index;
  private final Namespace namespace;
  private final ByteBuffer memory;
  private final long firstIndex;
  private Indexed<E> lastEntry;

  FileChannelJournalSegmentWriter(
      final FileChannel channel,
      final JournalSegment segment,
      final int maxEntrySize,
      final JournalIndex index,
      final Namespace namespace) {
    this.channel = channel;
    this.segment = segment;
    this.maxEntrySize = maxEntrySize;
    this.index = index;
    this.memory = ByteBuffer.allocate((maxEntrySize + Integer.BYTES + Integer.BYTES) * 2);
    memory.limit(0);
    this.namespace = namespace;
    this.firstIndex = segment.index();
    reset(0);
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

    try {
      // Serialize the entry.
      memory.clear();
      memory.position(Integer.BYTES + Integer.BYTES);
      try {
        namespace.serialize(entry, memory);
      } catch (final KryoException e) {
        throw new StorageException.TooLarge(
            "Entry size exceeds maximum allowed bytes (" + maxEntrySize + ")");
      }
      memory.flip();

      final int length = memory.limit() - (Integer.BYTES + Integer.BYTES);

      // Ensure there's enough space left in the buffer to store the entry.
      final long position = channel.position();
      if (segment.descriptor().maxSegmentSize() - position
          < length + Integer.BYTES + Integer.BYTES) {
        throw new BufferOverflowException();
      }

      // If the entry length exceeds the maximum entry size then throw an exception.
      if (length > maxEntrySize) {
        throw new StorageException.TooLarge(
            "Entry size " + length + " exceeds maximum allowed bytes (" + maxEntrySize + ")");
      }

      // Compute the checksum for the entry.
      final Checksum crc32 = new CRC32();
      crc32.update(
          memory.array(),
          Integer.BYTES + Integer.BYTES,
          memory.limit() - (Integer.BYTES + Integer.BYTES));
      final long checksum = crc32.getValue();

      // Create a single byte[] in memory for the entire entry and write it as a batch to the
      // underlying buffer.
      memory.putInt(0, length);
      memory.putInt(Integer.BYTES, (int) checksum);
      channel.write(memory);

      // Update the last entry with the correct index/term/length.
      final Indexed<E> indexedEntry = new Indexed<>(index, entry, length);
      this.lastEntry = indexedEntry;
      this.index.index(lastEntry, (int) position);
      return (Indexed<T>) indexedEntry;
    } catch (final IOException e) {
      throw new StorageException(e);
    }
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
    try {
      channel.position(JournalSegmentDescriptor.BYTES);
      memory.clear().flip();

      // Record the current buffer position.
      long position = channel.position();

      // Read more bytes from the segment if necessary.
      if (memory.remaining() < maxEntrySize) {
        memory.clear();
        channel.read(memory);
        channel.position(position);
        memory.flip();
      }

      // Read the entry length.
      memory.mark();
      int length = memory.getInt();

      // If the length is non-zero, read the entry.
      while (0 < length && length <= maxEntrySize && (index == 0 || nextIndex <= index)) {

        // Read the checksum of the entry.
        final long checksum = memory.getInt() & 0xFFFFFFFFL;

        // Compute the checksum for the entry bytes.
        final Checksum crc32 = new CRC32();
        crc32.update(memory.array(), memory.position(), length);

        // If the stored checksum equals the computed checksum, return the entry.
        if (checksum == crc32.getValue()) {
          final int limit = memory.limit();
          memory.limit(memory.position() + length);
          final E entry = namespace.deserialize(memory);
          memory.limit(limit);
          lastEntry = new Indexed<>(nextIndex, entry, length);
          this.index.index(lastEntry, (int) position);
          nextIndex++;
        } else {
          break;
        }

        // Update the current position for indexing.
        position = channel.position() + memory.position();

        // Read more bytes from the segment if necessary.
        if (memory.remaining() < maxEntrySize) {
          channel.position(position);
          memory.clear();
          channel.read(memory);
          channel.position(position);
          memory.flip();
        }

        memory.mark();
        length = memory.getInt();
      }

      // Reset the buffer to the previous mark.
      channel.position(channel.position() + memory.reset().position());
    } catch (final BufferUnderflowException e) {
      try {
        channel.position(channel.position() + memory.reset().position());
      } catch (final IOException e2) {
        throw new StorageException(e2);
      }
    } catch (final IOException e) {
      throw new StorageException(e);
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

    try {
      // Truncate the index.
      this.index.truncate(index);

      if (index < segment.index()) {
        channel.position(JournalSegmentDescriptor.BYTES);
        channel.write(zero());
        channel.position(JournalSegmentDescriptor.BYTES);
      } else {
        // Reset the writer to the given index.
        reset(index);

        // Zero entries after the given index.
        final long position = channel.position();
        channel.write(zero());
        channel.position(position);
      }
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void flush() {
    try {
      if (channel.isOpen()) {
        channel.force(true);
      }
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void close() {
    flush();
  }

  /**
   * Returns the size of the underlying buffer.
   *
   * @return The size of the underlying buffer.
   */
  public long size() {
    try {
      return channel.position();
    } catch (final IOException e) {
      throw new StorageException(e);
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

  /**
   * Returns a boolean indicating whether the segment is full.
   *
   * @return Indicates whether the segment is full.
   */
  public boolean isFull() {
    return size() >= segment.descriptor().maxSegmentSize()
        || getNextIndex() - firstIndex >= segment.descriptor().maxEntries();
  }

  /** Returns the first index written to the segment. */
  public long firstIndex() {
    return firstIndex;
  }

  /** Returns a zeroed out byte buffer. */
  private ByteBuffer zero() {
    memory.clear();
    for (int i = 0; i < memory.limit(); i++) {
      memory.put(i, (byte) 0);
    }
    return memory;
  }
}
