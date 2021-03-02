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

import io.zeebe.journal.JournalRecord;
import io.zeebe.journal.StorageException.InvalidIndex;
import io.zeebe.journal.file.ChecksumGenerator;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Common methods used by SegmentWriter and SegmentReader to read records from a buffer. */
public final class JournalRecordReaderUtil {

  private final JournalRecordSerializer serializer;

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

    final int startPosition = buffer.position();
    try {
      final UnsafeBuffer directBuffer = new UnsafeBuffer(buffer.slice());
      if (!serializer.hasMetadata(directBuffer)) {
        return null;
      }
      final JournalRecordMetadata metadata = serializer.readMetadata(directBuffer);

      final int metadataLength = serializer.getMetadataLength(directBuffer);
      final var recordLength = (int) metadata.length(); // TODO int <-> long
      if (buffer.position() + metadataLength + recordLength > buffer.limit()) {
        // There is no valid record here
        return null;
      }

      // verify checksum
      buffer.position(startPosition + metadataLength);
      final long checksum = computeChecksum(buffer.slice(), recordLength);

      if (checksum != metadata.checksum()) {
        buffer.reset(); // TODO: Throw exception
        return null;
      }

      // Read record
      buffer.position(startPosition + metadataLength);
      final UnsafeBuffer recordBuffer = new UnsafeBuffer(buffer, buffer.position(), recordLength);
      final JournalIndexedRecord record = serializer.readRecord(recordBuffer);

      if (record != null && expectedIndex != record.index()) {
        buffer.reset();
        throw new InvalidIndex(
            "Expected to read a record with next index"); // TODO : Update exception type, message
      }
      buffer.position(startPosition + metadataLength + recordLength);
      return new PersistedJournalRecord(metadata, record);

    } catch (final BufferUnderflowException e) {
      buffer.reset();
    }
    return null;
  }

  private long computeChecksum(final ByteBuffer buffer, final int length) {
    final var checksumGenerator = new ChecksumGenerator();
    final var record = buffer.slice();
    record.limit(length);
    return checksumGenerator.compute(record);
  }
}
