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

import io.atomix.storage.StorageException;
import io.atomix.storage.journal.index.JournalIndex;
import io.atomix.storage.journal.index.Position;
import io.atomix.utils.serializer.Namespace;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.NoSuchElementException;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * Log segment reader.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
class FileChannelJournalSegmentReader<E> implements JournalReader<E> {
  private final FileChannel channel;
  private final int maxEntrySize;
  private final JournalIndex index;
  private final Namespace namespace;
  private final ByteBuffer memory;
  private final JournalSegment<E> segment;
  private Indexed<E> currentEntry;
  private Indexed<E> nextEntry;

  FileChannelJournalSegmentReader(
      final FileChannel channel,
      final JournalSegment<E> segment,
      final int maxEntrySize,
      final JournalIndex index,
      final Namespace namespace) {
    this.channel = channel;
    this.maxEntrySize = maxEntrySize;
    this.index = index;
    this.namespace = namespace;
    this.memory = ByteBuffer.allocate((maxEntrySize + Integer.BYTES + Integer.BYTES) * 2);
    this.segment = segment;
    reset();
  }

  @Override
  public boolean isEmpty() {
    try {
      return channel.size() == 0;
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public long getFirstIndex() {
    return segment.index();
  }

  @Override
  public long getLastIndex() {
    return segment.lastIndex();
  }

  @Override
  public long getCurrentIndex() {
    return currentEntry != null ? currentEntry.index() : 0;
  }

  @Override
  public Indexed<E> getCurrentEntry() {
    return currentEntry;
  }

  @Override
  public long getNextIndex() {
    return currentEntry != null ? currentEntry.index() + 1 : getFirstIndex();
  }

  @Override
  public boolean hasNext() {
    // If the next entry is null, check whether a next entry exists.
    if (nextEntry == null) {
      readNext();
    }
    return nextEntry != null;
  }

  @Override
  public Indexed<E> next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    // Set the current entry to the next entry.
    currentEntry = nextEntry;

    // Reset the next entry to null.
    nextEntry = null;

    // Read the next entry in the segment.
    readNext();

    // Return the current entry.
    return currentEntry;
  }

  @Override
  public void reset() {
    try {
      channel.position(JournalSegmentDescriptor.BYTES);
    } catch (final IOException e) {
      throw new StorageException(e);
    }
    memory.clear().limit(0);
    currentEntry = null;
    nextEntry = null;
    readNext();
  }

  @Override
  public void reset(final long index) {
    final long firstIndex = segment.index();
    final long lastIndex = segment.lastIndex();

    reset();

    final Position position = this.index.lookup(index - 1);
    if (position != null && position.index() >= firstIndex && position.index() <= lastIndex) {
      currentEntry = new Indexed<>(position.index() - 1, null, 0);
      try {
        channel.position(position.position());
        memory.clear().flip();
      } catch (final IOException e) {
        currentEntry = null;
        throw new StorageException(e);
      }

      nextEntry = null;
      readNext();
    }

    while (getNextIndex() < index && hasNext()) {
      next();
    }
  }

  @Override
  public void close() {
    // Do nothing. The parent reader manages the channel.
  }

  /** Reads the next entry in the segment. */
  @SuppressWarnings("unchecked")
  private void readNext() {
    // Compute the index of the next entry in the segment.
    final long index = getNextIndex();

    try {
      // Read more bytes from the segment if necessary.
      if (memory.remaining() < maxEntrySize) {
        final long position = channel.position() + memory.position();
        channel.position(position);
        memory.clear();
        channel.read(memory);
        channel.position(position);
        memory.flip();
      }

      // Mark the buffer so it can be reset if necessary.
      memory.mark();

      try {
        // Read the length of the entry.
        final int length = memory.getInt();

        // If the buffer length is zero then return.
        if (length <= 0 || length > maxEntrySize) {
          memory.reset().limit(memory.position());
          nextEntry = null;
          return;
        }

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
          nextEntry = new Indexed<>(index, entry, length);
        } else {
          memory.reset().limit(memory.position());
          nextEntry = null;
        }
      } catch (final BufferUnderflowException e) {
        memory.reset().limit(memory.position());
        nextEntry = null;
      }
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }
}
