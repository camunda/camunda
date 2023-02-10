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

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.JournalException.InvalidChecksum;
import io.camunda.zeebe.journal.JournalException.InvalidIndex;
import io.camunda.zeebe.journal.JournalException.SegmentFull;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.record.JournalRecordReaderUtil;
import io.camunda.zeebe.journal.record.JournalRecordSerializer;
import io.camunda.zeebe.journal.record.PersistedJournalRecord;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.RecordMetadata;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.journal.util.ChecksumGenerator;
import io.camunda.zeebe.util.Either;
import java.nio.BufferUnderflowException;
import java.nio.MappedByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Segment writer. */
final class SegmentWriter {

  private final MappedByteBuffer buffer;
  private final Segment segment;
  private final JournalIndex index;
  private final long firstIndex;
  private JournalRecord lastEntry;
  private boolean isOpen = true;
  private final JournalRecordReaderUtil recordUtil;
  private final ChecksumGenerator checksumGenerator = new ChecksumGenerator();
  private final JournalRecordSerializer serializer = new SBESerializer();
  private final MutableDirectBuffer writeBuffer = new UnsafeBuffer();
  private final int descriptorLength;

  SegmentWriter(
      final MappedByteBuffer buffer,
      final Segment segment,
      final JournalIndex index,
      final long lastFlushedIndex) {
    this.segment = segment;
    descriptorLength = segment.descriptor().length();
    recordUtil = new JournalRecordReaderUtil(serializer);
    this.index = index;
    firstIndex = segment.index();
    this.buffer = buffer;
    writeBuffer.wrap(buffer);
    reset(0, lastFlushedIndex);
  }

  long getLastIndex() {
    return lastEntry != null ? lastEntry.index() : segment.index() - 1;
  }

  JournalRecord getLastEntry() {
    return lastEntry;
  }

  long getNextIndex() {
    if (lastEntry != null) {
      return lastEntry.index() + 1;
    } else {
      return firstIndex;
    }
  }

  Either<SegmentFull, JournalRecord> append(final long asqn, final DirectBuffer data) {
    // Store the entry index.
    final long recordIndex = getNextIndex();

    // TODO: Should reject append if the asqn is not greater than the previous record

    final int startPosition = buffer.position();
    final int frameLength = FrameUtil.getLength();
    final int metadataLength = serializer.getMetadataLength();

    final RecordData indexedRecord = new RecordData(recordIndex, asqn, data);

    final var writeResult =
        writeRecord(startPosition + frameLength + metadataLength, indexedRecord);
    if (writeResult.isLeft()) {
      buffer.position(startPosition);
      return Either.left(writeResult.getLeft());
    }
    final int recordLength = writeResult.get();

    final long checksum =
        checksumGenerator.compute(
            buffer, startPosition + frameLength + metadataLength, recordLength);

    writeMetadata(startPosition, frameLength, recordLength, checksum);
    updateLastWrittenEntry(startPosition, frameLength, metadataLength);
    FrameUtil.writeVersion(buffer, startPosition);

    buffer.position(startPosition + frameLength + metadataLength + recordLength);
    return Either.right(lastEntry);
  }

  Either<SegmentFull, Void> append(final JournalRecord record) {
    final long nextIndex = getNextIndex();

    // If the entry's index is not the expected next index in the segment, fail the append.
    if (record.index() != nextIndex) {
      throw new InvalidIndex(
          String.format(
              "The record index is not sequential. Expected the next index to be %d, but the record to append has index %d",
              nextIndex, record.index()));
    }

    final int startPosition = buffer.position();
    final int frameLength = FrameUtil.getLength();
    final int metadataLength = serializer.getMetadataLength();

    final RecordData indexedRecord = new RecordData(record.index(), record.asqn(), record.data());

    final var writeResult =
        writeRecord(startPosition + frameLength + metadataLength, indexedRecord);
    if (writeResult.isLeft()) {
      buffer.position(startPosition);
      return Either.left(writeResult.getLeft());
    }

    final int recordLength = writeResult.get();
    final long checksum =
        checksumGenerator.compute(
            buffer, startPosition + frameLength + metadataLength, recordLength);

    if (record.checksum() != checksum) {
      buffer.position(startPosition);
      throw new InvalidChecksum(
          String.format("Failed to append record %s. Checksum does not match", record));
    }

    writeMetadata(startPosition, frameLength, recordLength, checksum);
    updateLastWrittenEntry(startPosition, frameLength, metadataLength);
    FrameUtil.writeVersion(buffer, startPosition);

    buffer.position(startPosition + frameLength + metadataLength + recordLength);
    return Either.right(null);
  }

  private void updateLastWrittenEntry(
      final int startPosition, final int frameLength, final int metadataLength) {
    final var metadata = serializer.readMetadata(writeBuffer, startPosition + frameLength);
    final var data = serializer.readData(writeBuffer, startPosition + frameLength + metadataLength);
    lastEntry = new PersistedJournalRecord(metadata, data);
    index.index(lastEntry, startPosition);
  }

  private void writeMetadata(
      final int startPosition, final int frameLength, final int recordLength, final long checksum) {
    final RecordMetadata recordMetadata = new RecordMetadata(checksum, recordLength);
    serializer.writeMetadata(recordMetadata, writeBuffer, startPosition + frameLength);
  }

  private Either<SegmentFull, Integer> writeRecord(
      final int offset, final RecordData indexedRecord) {
    final var recordLength = serializer.writeData(indexedRecord, writeBuffer, offset);
    if (recordLength.isLeft()) {
      return Either.left(new SegmentFull("Not enough space to write record"));
    }
    final int nextEntryOffset = offset + recordLength.get();
    invalidateNextEntry(nextEntryOffset);
    return Either.right(recordLength.get());
  }

  private void invalidateNextEntry(final int position) {
    if (position >= buffer.capacity()) {
      return;
    }

    FrameUtil.markAsIgnored(buffer, position);
  }

  private void reset(final long index) {
    reset(index, -1);
  }

  private void reset(final long index, final long lastFlushedIndex) {
    long nextIndex = firstIndex;

    // Clear the buffer indexes.
    buffer.position(descriptorLength);
    buffer.mark();
    int position = buffer.position();
    try {
      while ((index == 0 || nextIndex <= index) && FrameUtil.hasValidVersion(buffer)) {
        // read version so that buffer's position is advanced
        FrameUtil.readVersion(buffer);
        lastEntry = recordUtil.read(buffer, nextIndex);
        nextIndex++;
        this.index.index(lastEntry, position);
        buffer.mark();
        position = buffer.position();
      }
    } catch (final BufferUnderflowException e) {
      // Reached end of the segment
    } catch (final CorruptedJournalException e) {
      handleChecksumMismatch(e, nextIndex, lastFlushedIndex, position);
    } finally {
      buffer.reset();
    }
  }

  private void handleChecksumMismatch(
      final CorruptedJournalException e,
      final long nextIndex,
      final long lastFlushedIndex,
      final int position) {
    // entry wasn't acked (likely a partial write): it's safe to delete it
    if (nextIndex > lastFlushedIndex) {
      FrameUtil.markAsIgnored(buffer, position);
      buffer.position(position);
      buffer.mark();
      return;
    }

    throw e;
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
      buffer.position(descriptorLength);
      invalidateNextEntry(descriptorLength);
    } else {
      reset(index);
      invalidateNextEntry(buffer.position());
    }
  }

  void flush() {
    buffer.force();
  }

  void close() {
    if (isOpen) {
      isOpen = false;
      flush();
    }
  }
}
