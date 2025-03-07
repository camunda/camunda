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
package io.camunda.zeebe.journal.file;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Sets;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.util.Set;
import org.agrona.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log segment.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
final class Segment implements AutoCloseable, FlushableSegment {

  private static final ByteOrder ENDIANNESS = ByteOrder.LITTLE_ENDIAN;
  private static final Logger LOG = LoggerFactory.getLogger(Segment.class);

  private final SegmentFile file;
  private SegmentDescriptor descriptor;
  private final SegmentDescriptorSerializer descriptorSerializer;
  private final JournalIndex index;
  private final SegmentWriter writer;
  private final Set<SegmentReader> readers = Sets.newConcurrentHashSet();
  private final MappedByteBuffer buffer;
  private final JournalMetrics metrics;

  // This needs to be volatile in case the flushing is asynchronous
  private volatile boolean open = true;
  // This need to be volatile because both the writer and the readers access it concurrently
  private volatile boolean markedForDeletion = false;

  Segment(
      final SegmentFile file,
      final SegmentDescriptor descriptor,
      final SegmentDescriptorSerializer descriptorSerializer,
      final MappedByteBuffer buffer,
      final long lastWrittenAsqn,
      final JournalIndex index,
      final JournalMetrics metrics) {
    this.file = file;
    this.descriptor = descriptor;
    this.descriptorSerializer = descriptorSerializer;
    this.buffer = buffer;
    this.index = index;
    this.metrics = metrics;

    writer = createWriter(lastWrittenAsqn, metrics);
  }

  /**
   * Returns the segment ID.
   *
   * @return The segment ID.
   */
  long id() {
    return descriptor.id();
  }

  /**
   * Returns the segment's starting index.
   *
   * @return The segment's starting index.
   */
  long index() {
    return descriptor.index();
  }

  /**
   * Returns the last index in the segment.
   *
   * @return The last index in the segment.
   */
  @Override
  public long lastIndex() {
    return writer.getLastIndex();
  }

  /**
   * It's safe to sync a buffer via {@link MappedByteBuffer#force()} even after it has been unmapped
   * (e.g. via {@link IoUtil#unmap(ByteBuffer)}.
   *
   * <p>Calling {@code msync} or {@code FlushViewOfFile} on pages which are not mapped returns an
   * error, but does not generate a SIGSEGV nor a SIGBUS. Instead, it returns an error code.
   *
   * <p>We verified that on OpenJDK, this is handled by throwing an {@link UncheckedIOException}
   * with a message about being unable to allocate memory. There are no other exceptions (other than
   * the usual suspects, like null pointers) possible, so it's safe to assume that if we get such an
   * error on calling {@link MappedByteBuffer#force()}, but the segment is closed/deleted, then we
   * can safely ignore it (as flushing doesn't matter in that case).
   *
   * <p>{@inheritDoc}
   *
   * @throws UncheckedIOException if the operation failed but the segment is live
   */
  @Override
  public void flush() throws FlushException {
    final long lastIndex = lastIndex();

    try (final var ignored = metrics.observeSegmentFlush()) {
      buffer.force();
    } catch (final UncheckedIOException e) {
      if (isOpen()) {
        throw new FlushException(e.getCause());
      }

      LOG.debug("Flushing failed on a closed or deleted segment, and will be ignored");
      return;
    }

    LOG.trace(
        "Flushed segment {} from index {} to index {}",
        descriptor.id(),
        descriptor.index(),
        lastIndex);
  }

  /**
   * Returns the last application sequence number in the segment.
   *
   * @return The last application sequence number in the segment.
   */
  long lastAsqn() {
    return writer.getLastAsqn();
  }

  /**
   * Returns the segment file.
   *
   * @return The segment file.
   */
  SegmentFile file() {
    return file;
  }

  /**
   * Returns the segment descriptor.
   *
   * @return The segment descriptor.
   */
  SegmentDescriptor descriptor() {
    return descriptor;
  }

  /**
   * Returns the segment writer.
   *
   * @return The segment writer.
   */
  SegmentWriter writer() {
    checkOpen();
    return writer;
  }

  /**
   * Creates a new segment reader.
   *
   * @return A new segment reader.
   */
  SegmentReader createReader() {
    checkOpen();
    final SegmentReader reader =
        new SegmentReader(buffer.asReadOnlyBuffer().position(0).order(ENDIANNESS), this, index);
    readers.add(reader);
    return reader;
  }

  private SegmentWriter createWriter(final long lastWrittenAsqn, final JournalMetrics metrics) {
    return new SegmentWriter(buffer, this, index, lastWrittenAsqn, metrics);
  }

  /**
   * Removes the reader from this segment.
   *
   * @param reader the closed reader
   */
  void onReaderClosed(final SegmentReader reader) {
    readers.remove(reader);
    // When multiple readers are closed simultaneously, both readers might try to delete the file.
    // This is ok, as safeDelete is idempotent. Hence we keep it simple, and doesn't add more
    // concurrency control.
    if (markedForDeletion && readers.isEmpty()) {
      safeDelete();
    }
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
  boolean isOpen() {
    return open;
  }

  /** Closes the segment. */
  @Override
  public void close() {
    open = false;
    readers.forEach(SegmentReader::close);
    IoUtil.unmap(buffer);
  }

  /** Deletes the segment. */
  void delete() {
    open = false;
    markForDeletion();
    if (readers.isEmpty()) {
      safeDelete();
    }
  }

  private void safeDelete() {
    if (!readers.isEmpty()) {
      throw new JournalException(
          String.format(
              "Cannot delete segment file. There are %d readers referring to this segment.",
              readers.size()));
    }
    try {
      IoUtil.unmap(buffer);
      Files.deleteIfExists(file.getFileMarkedForDeletion());
    } catch (final IOException e) {
      LOG.warn(
          "Could not delete segment {}. File to delete {}. This can lead to increased disk usage.",
          this,
          file.getFileMarkedForDeletion(),
          e);
    }
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("id", id()).add("index", index()).toString();
  }

  private void markForDeletion() {
    if (markedForDeletion) {
      return;
    }

    final var target = file.getFileMarkedForDeletion();
    try {
      FileUtil.moveDurably(file.file().toPath(), target);
    } catch (final IOException e) {
      throw new JournalException(e);
    }
    markedForDeletion = true;
  }

  void updateDescriptor() {
    descriptor =
        descriptor.withUpdatedIndices(writer.getLastIndex(), writer.getLastEntryPosition());
    descriptorSerializer.writeTo(descriptor, buffer);
  }

  void resetLastEntryInDescriptor() {
    descriptor = descriptor.reset();
    // Update the descriptor to set the last index and position to current values
    updateDescriptor();
    // flush immediately to prevent inconsistencies between descriptor and actual last written entry
    buffer.force(0, descriptor.encodingLength());
  }
}
