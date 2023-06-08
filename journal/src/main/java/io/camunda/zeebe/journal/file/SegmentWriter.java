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

import static io.camunda.zeebe.journal.file.SegmentedJournal.ASQN_IGNORE;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.JournalException.InvalidAsqn;
import io.camunda.zeebe.journal.JournalException.InvalidChecksum;
import io.camunda.zeebe.journal.JournalException.InvalidIndex;
import io.camunda.zeebe.journal.JournalException.SegmentFull;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.record.JournalRecordReaderUtil;
import io.camunda.zeebe.journal.record.JournalRecordSerializer;
import io.camunda.zeebe.journal.record.PersistedJournalRecord;
import io.camunda.zeebe.journal.record.RecordMetadata;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.journal.util.ChecksumGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.nio.BufferUnderflowException;
import java.nio.MappedByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Segment writer. */
final class SegmentWriter {

  private static final Logger LOG = LoggerFactory.getLogger(SegmentWriter.class);

  private final MappedByteBuffer buffer;
  private final Segment segment;
  private final JournalIndex index;
  private final long firstIndex;
  private final long firstAsqn;
  private long lastAsqn;
  private JournalRecord lastEntry;
  private final JournalRecordReaderUtil recordUtil;
  private final ChecksumGenerator checksumGenerator = new ChecksumGenerator();
  private final JournalRecordSerializer serializer = new SBESerializer();
  private final MutableDirectBuffer writeBuffer = new UnsafeBuffer();
  private final int descriptorLength;
  private final JournalMetrics metrics;

  SegmentWriter(
      final MappedByteBuffer buffer,
      final Segment segment,
      final JournalIndex index,
      final long lastWrittenAsqn,
      final JournalMetrics metrics) {
    this.segment = segment;
    descriptorLength = segment.descriptor().length();
    recordUtil = new JournalRecordReaderUtil(serializer);
    this.index = index;
    firstIndex = segment.index();
    this.buffer = buffer;
    writeBuffer.wrap(buffer);
    firstAsqn = lastWrittenAsqn + 1;
    lastAsqn = lastWrittenAsqn;
    this.metrics = metrics;
    reset(0, false);
  }

  long getLastIndex() {
    return lastEntry != null ? lastEntry.index() : segment.index() - 1;
  }

  long getNextIndex() {
    if (lastEntry != null) {
      return lastEntry.index() + 1;
    } else {
      return firstIndex;
    }
  }

  public long getLastAsqn() {
    return lastAsqn;
  }

  Either<SegmentFull, JournalRecord> append(final JournalRecord record) {
    return append(
        record.index(),
        record.asqn(),
        new DirectBufferWriter().wrap(record.data()),
        record.checksum());
  }

  Either<SegmentFull, JournalRecord> append(final long asqn, final BufferWriter recordDataWriter) {
    return append(getNextIndex(), asqn, recordDataWriter, null);
  }

  private Either<SegmentFull, JournalRecord> append(
      final Long entryIndex,
      final long asqn,
      final BufferWriter recordDataWriter,
      final Long expectedChecksum) {
    final long nextIndex = getNextIndex();

    // If the entry's index is not the expected next index in the segment, fail the append.
    if (entryIndex != nextIndex) {
      throw new InvalidIndex(
          String.format(
              "The record index is not sequential. Expected the next index to be %d, but the record to append has index %d",
              nextIndex, entryIndex));
    }

    if (asqn != SegmentedJournal.ASQN_IGNORE && asqn <= lastAsqn) {
      throw new InvalidAsqn(
          String.format(
              "The records asqn is not big enough. Expected it to be bigger than %d but was %d",
              lastAsqn, asqn));
    }

    final int startPosition = buffer.position();
    final int frameLength = FrameUtil.getLength();
    final int metadataLength = serializer.getMetadataLength();

    final var writeResult =
        writeRecord(
            getNextIndex(), asqn, startPosition + frameLength + metadataLength, recordDataWriter);
    if (writeResult.isLeft()) {
      buffer.position(startPosition);
      return Either.left(writeResult.getLeft());
    }

    final int recordLength = writeResult.get();

    final long checksum =
        checksumGenerator.compute(
            buffer, startPosition + frameLength + metadataLength, recordLength);

    if (expectedChecksum != null && expectedChecksum != checksum) {
      buffer.position(startPosition);
      throw new InvalidChecksum(
          String.format(
              "Failed to append record. Checksum %d does not match the expected %d.",
              checksum, expectedChecksum));
    }

    writeMetadata(startPosition, frameLength, recordLength, checksum);
    updateLastWrittenEntry(startPosition, frameLength, metadataLength, recordLength);
    FrameUtil.writeVersion(buffer, startPosition);

    final int appendedBytes = frameLength + metadataLength + recordLength;
    buffer.position(startPosition + appendedBytes);
    metrics.observeAppend(appendedBytes);
    return Either.right(lastEntry);
  }

  Either<SegmentFull, JournalRecord> append(
      final long entryIndex, final long expectedChecksum, final byte[] serializedRecord) {
    final long nextIndex = getNextIndex();

    // If the entry's index is not the expected next index in the segment, fail the append.
    if (entryIndex != nextIndex) {
      throw new InvalidIndex(
          String.format(
              "The record index is not sequential. Expected the next index to be %d, but the record to append has index %d",
              nextIndex, entryIndex));
    }

    final int startPosition = buffer.position();
    final int frameLength = FrameUtil.getLength();
    final int recordLength = serializedRecord.length;
    final int metadataLength = serializer.getMetadataLength();

    if (startPosition + frameLength + metadataLength + recordLength > buffer.capacity()) {
      return Either.left(new SegmentFull("Not enough space to write record"));
    }

    writeBuffer.putBytes(startPosition + frameLength + metadataLength, serializedRecord);

    final long checksum =
        checksumGenerator.compute(
            buffer, startPosition + frameLength + metadataLength, recordLength);

    if (expectedChecksum != checksum) {
      buffer.position(startPosition);
      throw new InvalidChecksum(
          String.format(
              "Failed to append record. Checksum %d does not match the expected %d.",
              checksum, expectedChecksum));
    }

    writeMetadata(startPosition, frameLength, recordLength, checksum);
    updateLastWrittenEntry(startPosition, frameLength, metadataLength, recordLength);
    FrameUtil.writeVersion(buffer, startPosition);

    final int appendedBytes = frameLength + metadataLength + recordLength;
    buffer.position(startPosition + appendedBytes);
    metrics.observeAppend(appendedBytes);
    return Either.right(lastEntry);
  }

  private void updateLastWrittenEntry(
      final int startPosition,
      final int frameLength,
      final int metadataLength,
      final int recordLength) {
    final var metadata = serializer.readMetadata(writeBuffer, startPosition + frameLength);
    final var data = serializer.readData(writeBuffer, startPosition + frameLength + metadataLength);
    lastEntry =
        new PersistedJournalRecord(
            metadata,
            data,
            new UnsafeBuffer(
                writeBuffer, startPosition + frameLength, metadataLength + recordLength));
    updateLastAsqn(lastEntry.asqn());
    index.index(lastEntry, startPosition);
  }

  private void updateLastAsqn(final long asqn) {
    lastAsqn = asqn != ASQN_IGNORE ? asqn : lastAsqn;
  }

  private void writeMetadata(
      final int startPosition, final int frameLength, final int recordLength, final long checksum) {
    final RecordMetadata recordMetadata = new RecordMetadata(checksum, recordLength);
    serializer.writeMetadata(recordMetadata, writeBuffer, startPosition + frameLength);
  }

  private Either<SegmentFull, Integer> writeRecord(
      final long index, final long asqn, final int offset, final BufferWriter recordDataWriter) {
    final var recordLength =
        serializer.writeData(index, asqn, recordDataWriter, writeBuffer, offset);
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

  private void reset(final long index, final boolean detectCorruption) {
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
        updateLastAsqn(lastEntry.asqn());
        nextIndex++;
        this.index.index(lastEntry, position);
        buffer.mark();
        position = buffer.position();
      }
    } catch (final BufferUnderflowException e) {
      // Reached end of the segment
    } catch (final CorruptedJournalException e) {
      if (detectCorruption) {
        throw e;
      }
      resetPartiallyWrittenEntry(e, position);
    } finally {
      buffer.reset();
    }
  }

  private void resetPartiallyWrittenEntry(final CorruptedJournalException e, final int position) {
    LOG.debug(
        "{} Found a corrupted or partially written entry at position {}. Considering it as a partially written entry and resetting the position.",
        e.getMessage(),
        position);
    FrameUtil.markAsIgnored(buffer, position);
    buffer.position(position);
    buffer.mark();
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
    lastAsqn = firstAsqn - 1;

    if (index < segment.index()) {
      buffer.position(descriptorLength);
      invalidateNextEntry(descriptorLength);
    } else {
      reset(index, true);
      invalidateNextEntry(buffer.position());
    }
  }
}
