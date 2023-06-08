/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.record;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.JournalException.InvalidIndex;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.util.ChecksumGenerator;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Common methods used by SegmentWriter and SegmentReader to read records from a buffer. */
public final class JournalRecordReaderUtil {

  private final JournalRecordSerializer serializer;
  private final ChecksumGenerator checksumGenerator = new ChecksumGenerator();

  public JournalRecordReaderUtil(final JournalRecordSerializer serializer) {
    this.serializer = serializer;
  }

  /**
   * Reads the JournalRecord in the buffer at the current position. After the methods returns, the
   * position of {@code buffer} will be advanced to the next record.
   */
  public JournalRecord read(final ByteBuffer buffer, final long expectedIndex) {
    // Mark the buffer so it can be reset if necessary.
    buffer.mark();

    if (buffer.position() + serializer.getMetadataLength() > buffer.limit()) {
      // This should never happen as this method is invoked always after hasNext() returns true
      throw new CorruptedJournalException(
          "Expected to read a record, but reached the end of the segment.");
    }

    final int startPosition = buffer.position();

    final UnsafeBuffer directBuffer = new UnsafeBuffer(buffer.slice());

    final RecordMetadata metadata = serializer.readMetadata(directBuffer, 0);

    final int metadataLength = serializer.getMetadataLength(directBuffer, 0);
    final var recordLength = metadata.length();
    if (buffer.position() + metadataLength + recordLength > buffer.limit()) {
      // There is no valid record here. This should not happen, if we have magic headers before
      // each record.
      throw new CorruptedJournalException(
          String.format(
              "Expected to read a record at position %d, with metadata %s, but reached the end of the segment.",
              buffer.position(), metadata));
    }

    // verify checksum
    final long checksum =
        checksumGenerator.compute(buffer, startPosition + metadataLength, recordLength);

    if (checksum != metadata.checksum()) {
      buffer.reset();
      throw new CorruptedJournalException(
          "Record's checksum (%d) doesn't match checksum stored in metadata (%d)."
              .formatted(checksum, metadata.checksum()));
    }

    // Read record
    final RecordData record = serializer.readData(directBuffer, metadataLength);

    if (record != null && expectedIndex != record.index()) {
      buffer.reset();
      throw new InvalidIndex(
          String.format(
              "Expected to read a record with next index %d, but found %d",
              expectedIndex, record.index()));
    }
    buffer.position(startPosition + metadataLength + recordLength);
    return new PersistedJournalRecord(
        metadata, record, new UnsafeBuffer(buffer, startPosition + metadataLength, recordLength));
  }
}
