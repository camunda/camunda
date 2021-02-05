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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Sets;
import io.zeebe.journal.StorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

/**
 * Log segment.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
class JournalSegment implements AutoCloseable {

  private final JournalSegmentFile file;
  private final JournalSegmentDescriptor descriptor;
  private final int maxEntrySize;
  private final JournalIndex index;
  private final MappedJournalSegmentWriter writer;
  private final Set<MappedJournalSegmentReader> readers = Sets.newConcurrentHashSet();
  private boolean open = true;

  public JournalSegment(
      final JournalSegmentFile file,
      final JournalSegmentDescriptor descriptor,
      final int maxEntrySize,
      final JournalIndex journalIndex) {
    this.file = file;
    this.descriptor = descriptor;
    this.maxEntrySize = maxEntrySize;
    index = journalIndex;
    writer = createWriter(file, maxEntrySize);
  }

  /**
   * Returns the segment ID.
   *
   * @return The segment ID.
   */
  public long id() {
    return descriptor.id();
  }

  /**
   * Returns the segment version.
   *
   * @return The segment version.
   */
  public long version() {
    return descriptor.version();
  }

  /**
   * Returns the segment's starting index.
   *
   * @return The segment's starting index.
   */
  public long index() {
    return descriptor.index();
  }

  /**
   * Returns the last index in the segment.
   *
   * @return The last index in the segment.
   */
  public long lastIndex() {
    return writer.getLastIndex();
  }

  /**
   * Returns the segment file.
   *
   * @return The segment file.
   */
  public JournalSegmentFile file() {
    return file;
  }

  /**
   * Returns the segment descriptor.
   *
   * @return The segment descriptor.
   */
  public JournalSegmentDescriptor descriptor() {
    return descriptor;
  }

  /**
   * Returns a boolean value indicating whether the segment is empty.
   *
   * @return Indicates whether the segment is empty.
   */
  public boolean isEmpty() {
    return length() == 0;
  }

  /**
   * Returns the segment length.
   *
   * @return The segment length.
   */
  public long length() {
    return writer.getNextIndex() - index();
  }

  /**
   * Returns the segment writer.
   *
   * @return The segment writer.
   */
  public MappedJournalSegmentWriter writer() {
    checkOpen();
    return writer;
  }

  /**
   * Creates a new segment reader.
   *
   * @return A new segment reader.
   */
  MappedJournalSegmentReader createReader() {
    checkOpen();
    return new MappedJournalSegmentReader(file, this, maxEntrySize, index);
  }

  private MappedJournalSegmentWriter createWriter(
      final JournalSegmentFile file, final int maxEntrySize) {
    return new MappedJournalSegmentWriter(file, this, maxEntrySize, index);
  }

  /**
   * Removes the reader from this segment.
   *
   * @param reader the closed reader
   */
  void onReaderClosed(final MappedJournalSegmentReader reader) {
    readers.remove(reader);
  }

  /** Checks whether the segment is open. */
  private void checkOpen() {
    checkState(open, "Segment not open");
  }

  /**
   * Returns a boolean indicating whether the segment is open.
   *
   * @return indicates whether the segment is open
   */
  public boolean isOpen() {
    return open;
  }

  /** Closes the segment. */
  @Override
  public void close() {
    writer.close();
    readers.forEach(MappedJournalSegmentReader::close);
    open = false;
  }

  void compactIndex(final long index) {
    this.index.deleteUntil(index);
  }

  /** Deletes the segment. */
  public void delete() {
    try {
      Files.deleteIfExists(file.file().toPath());
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("id", id())
        .add("version", version())
        .add("index", index())
        .toString();
  }
}
