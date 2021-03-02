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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public interface JournalRecordSerializer {

  /**
   * Writes a {@link JournalIndexedRecord} to the buffer)
   *
   * @param record to write
   * @param buffer to which the record will be written
   * @return the number of bytes that were written to the buffer
   */
  int write(JournalIndexedRecord record, MutableDirectBuffer buffer);

  /**
   * Writes a {@link JournalRecordMetadata} to the buffer)
   *
   * @param metadata to write
   * @param buffer to which the metadata will be written
   * @return the number of bytes that were written to the buffer
   */
  int write(JournalRecordMetadata metadata, MutableDirectBuffer buffer);

  /**
   * Returns the number of bytes required to write a {@link JournalRecordMetadata} to a buffer. The
   * length returned by this method must be equal to the length returned by {@link
   * JournalRecordSerializer#write(JournalRecordMetadata, MutableDirectBuffer)}
   *
   * @return the expected length of a serialized metadata
   */
  int getMetadataLength();

  /**
   * Returns the number of bytes required to write a {@link JournalIndexedRecord} to a buffer. The
   * length returned by this method must be equal to the length returned by {@code write(record,
   * buffer)}.
   *
   * @param record for which the length should be calculated
   * @return the expected length of serialized record
   */
  int getSerializedLength(JournalIndexedRecord record);

  /**
   * Checks if a valid metadata can be read from the buffer.
   *
   * @param buffer to read
   * @return true if a valid metadata exists, false otherwise.
   */
  boolean hasMetadata(DirectBuffer buffer);

  /**
   * Reads the {@link JournalRecordMetadata} from the buffer at offset 0. A valid record must exist
   * in the buffer at this position.
   *
   * @param buffer to read
   * @return a journal record metadata that is read.
   */
  JournalRecordMetadata readMetadata(DirectBuffer buffer);

  /**
   * Reads the {@link JournalIndexedRecord} from the buffer at offset 0. A valid record must exist
   * in the buffer at this position.
   *
   * @param buffer to read
   * @return a journal indexed record that is read.
   */
  JournalIndexedRecord readRecord(DirectBuffer buffer);

  /**
   * Returns the length of the serialized {@link JournalRecordMetadata} at the buffer.
   *
   * @param buffer to read
   * @return the length of the metadata
   */
  int getMetadataLength(DirectBuffer buffer);
}
