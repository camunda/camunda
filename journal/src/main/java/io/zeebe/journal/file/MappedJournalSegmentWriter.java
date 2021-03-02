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

import io.zeebe.journal.JournalRecord;
import io.zeebe.journal.StorageException;
import io.zeebe.journal.StorageException.InvalidChecksum;
import io.zeebe.journal.StorageException.InvalidIndex;
import io.zeebe.journal.file.record.JournalIndexedRecord;
import io.zeebe.journal.file.record.JournalIndexedRecordImpl;
import io.zeebe.journal.file.record.JournalRecordMetadata;
import io.zeebe.journal.file.record.JournalRecordMetadataImpl;
import io.zeebe.journal.file.record.JournalRecordReaderUtil;
import io.zeebe.journal.file.record.JournalRecordSerializer;
import io.zeebe.journal.file.record.PersistedJournalRecord;
import io.zeebe.journal.file.record.SBESerializer;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.IoUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Segment writer. */
class MappedJournalSegmentWriter {

  private final MappedByteBuffer buffer;
  private final JournalSegment segment;
  private final JournalIndex index;
  private final long firstIndex;
  private JournalRecord lastEntry;
  private boolean isOpen = true;
  private final JournalRecordReaderUtil recordUtil;
  private final int maxEntrySize;
  private final ChecksumGenerator checksumGenerator = new ChecksumGenerator();
  private final JournalRecordSerializer serializer = new SBESerializer();

  MappedJournalSegmentWriter(
      final JournalSegmentFile file,
      final JournalSegment segment,
      final JournalIndex index,
      final int maxEntrySize) {
    this.segment = segment;
    this.maxEntrySize = maxEntrySize;
    recordUtil = new JournalRecordReaderUtil(serializer);
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
    final long recordIndex = getNextIndex();

    // TODO: Should reject append if the asqn is not greater than the previous record

    final MutableDirectBuffer writeBuffer = new UnsafeBuffer();
    final int startPosition = buffer.position();
    final int metadataLength = serializer.getMetadataLength();

    final JournalIndexedRecord indexedRecord =
        new JournalIndexedRecordImpl(recordIndex, asqn, data);
    checkCanWrite(buffer, indexedRecord);
    final int recordLength =
        writeRecord(buffer, writeBuffer, startPosition + metadataLength, indexedRecord);

    final long checksum = computeChecksum(buffer, startPosition + metadataLength, recordLength);

    writeMetadata(buffer, writeBuffer, startPosition, metadataLength, recordLength, checksum);

    updateLastWrittenEntry(buffer, startPosition, metadataLength, recordLength);

    buffer.position(startPosition + metadataLength + recordLength);
    return lastEntry;
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

    final MutableDirectBuffer writeBuffer = new UnsafeBuffer();
    final int startPosition = buffer.position();
    final int metadataLength = serializer.getMetadataLength();

    final JournalIndexedRecord indexedRecord =
        new JournalIndexedRecordImpl(record.index(), record.asqn(), record.data());
    checkCanWrite(buffer, indexedRecord);
    final int recordLength =
        writeRecord(buffer, writeBuffer, startPosition + metadataLength, indexedRecord);

    final long checksum = computeChecksum(buffer, startPosition + metadataLength, recordLength);

    if (record.checksum() != checksum) {
      buffer.position(startPosition);
      throw new InvalidChecksum(
          String.format("Failed to append record %s. Checksum does not match", record));
    }

    writeMetadata(buffer, writeBuffer, startPosition, metadataLength, recordLength, checksum);

    updateLastWrittenEntry(buffer, startPosition, metadataLength, recordLength);

    buffer.position(startPosition + metadataLength + recordLength);
  }

  private void updateLastWrittenEntry(
      final ByteBuffer buffer,
      final int startPosition,
      final int metadataLength,
      final int recordLength) {
    final JournalRecordMetadata metadata =
        serializer.readMetadata(new UnsafeBuffer(buffer, startPosition, metadataLength));
    final JournalIndexedRecord recordRead =
        serializer.readRecord(
            new UnsafeBuffer(buffer, startPosition + metadataLength, recordLength));

    lastEntry = new PersistedJournalRecord(metadata, recordRead);
    index.index(lastEntry, startPosition);
  }

  private void writeMetadata(
      final ByteBuffer buffer,
      final MutableDirectBuffer writeBuffer,
      final int startPosition,
      final int metadataLength,
      final int recordLength,
      final long checksum) {
    writeBuffer.wrap(buffer, startPosition, metadataLength);
    final JournalRecordMetadata recordMetadata =
        new JournalRecordMetadataImpl(checksum, recordLength);
    serializer.write(recordMetadata, writeBuffer);
  }

  private int writeRecord(
      final ByteBuffer buffer,
      final MutableDirectBuffer writeBuffer,
      final int offset,
      final JournalIndexedRecord indexedRecord) {
    writeBuffer.wrap(buffer, offset, buffer.limit() - offset);
    final var recordLength = serializer.write(indexedRecord, writeBuffer);
    return recordLength;
  }

  private void checkCanWrite(final ByteBuffer buffer, final JournalIndexedRecord indexedRecord) {
    final var recordOffset = serializer.getMetadataLength();

    final int estimatedRecordLength = serializer.getSerializedLength(indexedRecord);
    if (estimatedRecordLength > maxEntrySize) {
      throw new StorageException.TooLarge(
          "Entry size "
              + estimatedRecordLength
              + " exceeds maximum allowed bytes ("
              + maxEntrySize
              + ")");
    }
    if (buffer.position() + recordOffset + estimatedRecordLength > buffer.limit()) {
      throw new BufferOverflowException();
    }
  }

  private long computeChecksum(final ByteBuffer buffer, final int offset, final int length) {
    buffer.position(offset);
    final var record = buffer.slice();
    record.limit(length);
    return checksumGenerator.compute(record);
  }

  private void reset(final long index) {
    long nextIndex = firstIndex;

    // Clear the buffer indexes.
    buffer.position(JournalSegmentDescriptor.BYTES);
    buffer.mark();
    try {
      while (index == 0 || nextIndex <= index) {
        final var nextEntry = recordUtil.read(buffer, nextIndex);
        if (nextEntry == null) {
          break;
        }
        lastEntry = nextEntry;
        nextIndex++;
        buffer.mark();
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
      buffer.putLong(0);
      buffer.putLong(0);
      buffer.position(JournalSegmentDescriptor.BYTES);
    } else {
      // Reset the writer to the given index.
      reset(index);

      // Zero entries after the given index.
      final int position = buffer.position();
      buffer.putLong(0);
      buffer.putLong(0);
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
