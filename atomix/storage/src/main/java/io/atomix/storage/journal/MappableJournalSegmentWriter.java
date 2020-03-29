/*
 * Copyright 2018-present Open Networking Foundation
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
import io.atomix.utils.serializer.Namespace;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/** Mappable log segment writer. */
class MappableJournalSegmentWriter<E> implements JournalWriter<E> {

  private final FileChannel channel;
  private final JournalSegment<E> segment;
  private final int maxEntrySize;
  private final JournalIndex index;
  private final Namespace namespace;
  private JournalWriter<E> writer;

  MappableJournalSegmentWriter(
      final FileChannel channel,
      final JournalSegment<E> segment,
      final int maxEntrySize,
      final JournalIndex index,
      final Namespace namespace) {
    this.channel = channel;
    this.segment = segment;
    this.maxEntrySize = maxEntrySize;
    this.index = index;
    this.namespace = namespace;
    this.writer =
        new FileChannelJournalSegmentWriter<>(channel, segment, maxEntrySize, index, namespace);
  }

  /**
   * Maps the segment writer into memory, returning the mapped buffer.
   *
   * @return the buffer that was mapped into memory
   */
  MappedByteBuffer map() {
    if (writer instanceof MappedJournalSegmentWriter) {
      return ((MappedJournalSegmentWriter<E>) writer).buffer();
    }

    try {
      final JournalWriter<E> writer = this.writer;
      final MappedByteBuffer buffer =
          channel.map(FileChannel.MapMode.READ_WRITE, 0, segment.descriptor().maxSegmentSize());
      this.writer =
          new MappedJournalSegmentWriter<>(buffer, segment, maxEntrySize, index, namespace);
      writer.close();
      return buffer;
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  /** Unmaps the mapped buffer. */
  void unmap() {
    if (writer instanceof MappedJournalSegmentWriter) {
      final JournalWriter<E> writer = this.writer;
      this.writer =
          new FileChannelJournalSegmentWriter<>(channel, segment, maxEntrySize, index, namespace);
      writer.close();
    }
  }

  MappedByteBuffer buffer() {
    final JournalWriter<E> writer = this.writer;
    if (writer instanceof MappedJournalSegmentWriter) {
      return ((MappedJournalSegmentWriter<E>) writer).buffer();
    }
    return null;
  }

  /**
   * Returns the writer's first index.
   *
   * @return the writer's first index
   */
  public long firstIndex() {
    return segment.index();
  }

  /**
   * Returns the size of the segment.
   *
   * @return the size of the segment
   */
  public int size() {
    try {
      return (int) channel.size();
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public long getLastIndex() {
    return writer.getLastIndex();
  }

  @Override
  public Indexed<E> getLastEntry() {
    return writer.getLastEntry();
  }

  @Override
  public long getNextIndex() {
    return writer.getNextIndex();
  }

  @Override
  public <T extends E> Indexed<T> append(final T entry) {
    return writer.append(entry);
  }

  @Override
  public void append(final Indexed<E> entry) {
    writer.append(entry);
  }

  @Override
  public void commit(final long index) {
    writer.commit(index);
  }

  @Override
  public void reset(final long index) {
    writer.reset(index);
  }

  @Override
  public void truncate(final long index) {
    writer.truncate(index);
  }

  @Override
  public void flush() {
    writer.flush();
  }

  @Override
  public void close() {
    writer.close();
    try {
      channel.close();
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }
}
