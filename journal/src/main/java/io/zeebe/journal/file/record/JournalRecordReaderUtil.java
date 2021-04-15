/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.journal.file.record;

import io.zeebe.journal.JournalException.InvalidIndex;
import io.zeebe.journal.JournalRecord;
import io.zeebe.journal.file.ChecksumGenerator;
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
      throw new CorruptedLogException(
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
      throw new CorruptedLogException(
          String.format(
              "Expected to read a record at position %d, with metadata %s, but reached the end of the segment.",
              buffer.position(), metadata));
    }

    // verify checksum
    final long checksum =
        checksumGenerator.compute(buffer, startPosition + metadataLength, recordLength);

    if (checksum != metadata.checksum()) {
      buffer.reset();
      throw new CorruptedLogException(
          "Record doesn't match checksum. Log segment may be corrupted.");
    }

    // Read record
    final RecordData record = serializer.readData(directBuffer, metadataLength, recordLength);

    if (record != null && expectedIndex != record.index()) {
      buffer.reset();
      throw new InvalidIndex(
          String.format(
              "Expected to read a record with next index %d, but found %d",
              expectedIndex, record.index()));
    }
    buffer.position(startPosition + metadataLength + recordLength);
    return new PersistedJournalRecord(metadata, record);
  }
}
