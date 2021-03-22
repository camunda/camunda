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
   * Writes a {@link RecordData} to the buffer). Throws {@link java.nio.BufferOverflowException} if
   * there is not enough space to write the record.
   *
   * @param record to write
   * @param buffer to which the record will be written
   * @param offset the offset in the buffer at which the data will be written
   * @return the number of bytes that were written to the buffer
   */
  int writeData(RecordData record, MutableDirectBuffer buffer, int offset);

  /**
   * Writes a {@link RecordMetadata} to the buffer)
   *
   * @param metadata to write
   * @param buffer to which the metadata will be written
   * @param offset the offset in the buffer at which the metadata will be written
   * @return the number of bytes that were written to the buffer
   */
  int writeMetadata(RecordMetadata metadata, MutableDirectBuffer buffer, int offset);

  /**
   * Returns the number of bytes required to write a {@link RecordMetadata} to a buffer. The length
   * returned by this method must be equal to the length returned by {@link
   * JournalRecordSerializer#writeMetadata(RecordMetadata, MutableDirectBuffer, int)}
   *
   * @return the expected length of a serialized metadata
   */
  int getMetadataLength();

  /**
   * Reads the {@link RecordMetadata} from the buffer at offset 0. A valid record must exist in the
   * buffer at this position.
   *
   * @param buffer to read
   * @param offset the offset in the buffer at which the metadata will be read from
   * @return a journal record metadata that is read.
   */
  RecordMetadata readMetadata(DirectBuffer buffer, int offset);

  /**
   * Reads the {@link RecordData} from the buffer at offset 0. A valid record must exist in the
   * buffer at this position.
   *
   * @param buffer to read
   * @param offset the offset in the buffer at which the data will be read from
   * @return a journal indexed record that is read.
   */
  RecordData readData(DirectBuffer buffer, int offset, int length);

  /**
   * Returns the length of the serialized {@link RecordMetadata} in the buffer.
   *
   * @param buffer to read
   * @param offset the offset in the buffer at which the metadata will be read from
   * @return the length of the metadata
   */
  int getMetadataLength(DirectBuffer buffer, int offset);
}
