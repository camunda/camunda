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
import io.zeebe.journal.file.ChecksumGenerator;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * Common methods used by SegmentWriter and MappedJournalSegmentReader to read records from a
 * buffer.
 */
public final class JournalRecordReaderUtil {
  private final JournalRecordBufferReader serializer = new KryoSerializer();

  private final int maxEntrySize;
  private final ChecksumGenerator checksumGenerator = new ChecksumGenerator();

  public JournalRecordReaderUtil(final int maxEntrySize) {
    this.maxEntrySize = maxEntrySize;
  }

  /**
   * Reads the JournalRecord in the buffer at the current position. After the methods returns, the
   * position of {@code buffer} will be advanced to the next record.
   */
  public JournalRecord read(final ByteBuffer buffer, final long expectedIndex) {
    // Mark the buffer so it can be reset if necessary.
    buffer.mark();

    try {
      // Read the length of the record.
      final int length = buffer.getInt();

      // If the buffer length is zero then return.
      if (length <= 0 || length > maxEntrySize) {
        buffer.reset();
        return null;
      }

      final ByteBuffer slice = buffer.slice();
      slice.limit(length);

      // If the stored checksum equals the computed checksum, return the record.
      slice.rewind();
      final JournalRecord record = serializer.read(slice);
      final var checksum = record.checksum();
      // TODO: checksum should also include asqn.
      // TODO: It is now copying the data to calculate the checksum. This should be fixed.
      final var expectedChecksum = checksumGenerator.compute(record.data());
      if (checksum != expectedChecksum || expectedIndex != record.index()) {
        buffer.reset();
        return null;
      }
      buffer.position(buffer.position() + length);
      buffer.mark();
      return record;

    } catch (final BufferUnderflowException e) {
      buffer.reset();
    }
    return null;
  }
}
