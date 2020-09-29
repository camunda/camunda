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
import io.atomix.storage.StorageLevel;
import io.atomix.storage.journal.index.JournalIndex;
import io.atomix.utils.serializer.Namespace;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

/** Mappable log segment reader. */
class MappableJournalSegmentReader<E> implements JournalReader<E> {
  private final JournalSegment<E> segment;
  private final FileChannel channel;
  private final int maxEntrySize;
  private final JournalIndex index;
  private final Namespace namespace;
  private JournalReader<E> reader;

  MappableJournalSegmentReader(
      final FileChannel channel,
      final JournalSegment<E> segment,
      final int maxEntrySize,
      final JournalIndex index,
      final Namespace namespace,
      final StorageLevel storageLevel) {
    this.channel = channel;
    this.segment = segment;
    this.maxEntrySize = maxEntrySize;
    this.index = index;
    this.namespace = namespace;

    // todo: move this logic out of this constructor
    if (storageLevel == StorageLevel.MAPPED) {
      final MappedByteBuffer buffer;
      try {
        buffer = channel.map(MapMode.READ_ONLY, 0, segment.descriptor().maxSegmentSize());
      } catch (final IOException e) {
        throw new StorageException(e);
      }
      reader = new MappedJournalSegmentReader<>(buffer, segment, maxEntrySize, index, namespace);
    } else {
      reader =
          new FileChannelJournalSegmentReader<>(channel, segment, maxEntrySize, index, namespace);
    }
  }

  /** Converts the reader to a mapped reader using the given buffer. */
  void map() {
    if (!(reader instanceof MappedJournalSegmentReader)) {
      final MappedByteBuffer buffer;
      try {
        buffer = channel.map(MapMode.READ_ONLY, 0, segment.descriptor().maxSegmentSize());
      } catch (final IOException e) {
        throw new StorageException(e);
      }
      final JournalReader<E> reader = this.reader;
      this.reader =
          new MappedJournalSegmentReader<>(buffer, segment, maxEntrySize, index, namespace);
      this.reader.reset(reader.getNextIndex());
      reader.close();
    }
  }

  /** Converts the reader to an unmapped reader. */
  void unmap() {
    // if Filechannelreader -> nothing to do (because nothing mapped)
    // if Mappedreader -> nothing to do
    // (because close does nothing and not necessary to create a new reader)
    if (reader instanceof MappedJournalSegmentReader) {
      reader.close();
    }
  }

  @Override
  public boolean isEmpty() {
    return reader.isEmpty();
  }

  @Override
  public long getFirstIndex() {
    return reader.getFirstIndex();
  }

  @Override
  public long getLastIndex() {
    return reader.getLastIndex();
  }

  @Override
  public long getCurrentIndex() {
    return reader.getCurrentIndex();
  }

  @Override
  public Indexed<E> getCurrentEntry() {
    return reader.getCurrentEntry();
  }

  @Override
  public long getNextIndex() {
    return reader.getNextIndex();
  }

  @Override
  public boolean hasNext() {
    return reader.hasNext();
  }

  @Override
  public Indexed<E> next() {
    return reader.next();
  }

  @Override
  public void reset() {
    reader.reset();
  }

  @Override
  public void reset(final long index) {
    reader.reset(index);
  }

  @Override
  public void close() {
    reader.close();
    try {
      channel.close();
    } catch (final IOException e) {
      throw new StorageException(e);
    } finally {
      segment.closeReader(this);
    }
  }
}
