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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/** Common methods used by SegmentWriter and SegmentReader to read records from a buffer. */
public final class JournalRecordReaderUtil {

  private final JournalRecordBufferReader serializer;

  public JournalRecordReaderUtil(final JournalRecordBufferReader serializer) {
    this.serializer = serializer;
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

      final JournalRecord record = serializer.read(buffer);
      if (record != null && expectedIndex != record.index()) {
        buffer.reset();
        return null;
      }
      return record;

    } catch (final BufferUnderflowException e) {
      buffer.reset();
    }
    return null;
  }
}
