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
import java.nio.file.StandardOpenOption;
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
      final JournalSegmentFile file,
      final JournalSegment<E> segment,
      final int maxEntrySize,
      final JournalIndex index,
      final Namespace namespace) {
    this.segment = segment;
    this.maxEntrySize = maxEntrySize;
    this.index = index;
    this.namespace = namespace;
    channel = file.openChannel(StandardOpenOption.READ);
    memory = ByteBuffer.allocate((maxEntrySize + Integer.BYTES + Integer.BYTES) * 2);
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
      currentEntry = new Indexed<>(position.index() - 1, null, 0, -1);
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
    try {
      channel.close();
    } catch (final IOException e) {
      throw new StorageException(e);
    }
    segment.onReaderClosed(this);
  }

  /** Reads the next entry in the segment. */
  private void readNext() {
    final long index = getNextIndex();

    try {
      // Mark the buffer so it can be reset if necessary.
      memory.mark();

      final var cantReadLength = memory.remaining() < Integer.BYTES;
      if (cantReadLength) {
        readBytesIntoBuffer();
        memory.mark();
      }

      final int length = memory.getInt();
      if (isLengthInvalid(length)) {
        return;
      }

      // we using a CRC32 - which is 32 byte checksum
      // remaining bytes need to be larger or equals to entry length + checksum length
      final var cantReadEntry = memory.remaining() < (length + Integer.BYTES);
      if (cantReadEntry) {
        readBytesIntoBuffer();
        memory.mark();
        // we don't need to read the length again
      }

      readNextEntry(index, length);

    } catch (final BufferUnderflowException e) {
      resetReading();
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  private void readNextEntry(final long index, final int length) {
    final long checksum = memory.getInt() & 0xFFFFFFFFL;

    if (isChecksumInvalid(checksum, length)) {
      resetReading();
      return;
    }

    // If the stored checksum equals the computed checksum, set the next entry.
    final int limit = memory.limit();
    memory.limit(memory.position() + length);
    final E entry = namespace.deserialize(memory);
    memory.limit(limit);
    nextEntry = new Indexed<>(index, entry, length, checksum);
  }

  private void resetReading() {
    memory.reset().limit(memory.position());
    nextEntry = null;
  }

  private boolean isChecksumInvalid(final long checksum, final int length) {
    // Compute the checksum for the entry bytes.
    final Checksum crc32 = new CRC32();
    crc32.update(memory.array(), memory.position(), length);

    return checksum != crc32.getValue();
  }

  private boolean isLengthInvalid(final int length) {
    // If the buffer length is zero then return.
    if (length <= 0 || length > maxEntrySize) {
      memory.reset().limit(memory.position());
      nextEntry = null;
      return true;
    }
    return false;
  }

  private void readBytesIntoBuffer() throws IOException {
    final long position = channel.position() + memory.position();
    channel.position(position);
    memory.clear();
    channel.read(memory);
    channel.position(position);
    memory.flip();
  }
}
