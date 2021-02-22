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

import io.zeebe.journal.file.JournalRecordMetadataEncoder;
import io.zeebe.journal.file.MessageHeaderEncoder;
import org.agrona.MutableDirectBuffer;

/**
 * A {@link PersistedJournalRecord} consists of two parts. The first part is JournalRecordMetadata.
 * The second part is JournalIndexedRecord.
 *
 * <p>{@link PersistableJournalRecordMetadata} is the JournalRecordMetadata which can be serialized
 * in to a buffer as part of a {@link PersistedJournalRecord}.
 */
final class PersistableJournalRecordMetadata {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final JournalRecordMetadataEncoder encoder = new JournalRecordMetadataEncoder();
  private long checksum;

  public void setChecksum(final long checksum) {
    this.checksum = checksum;
  }

  /**
   * Returns the length required to write this record to a buffer
   *
   * @return the length
   */
  public int getLength() {
    return headerEncoder.encodedLength() + encoder.sbeBlockLength();
  }

  public void write(final MutableDirectBuffer buffer, final int offset) {
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(encoder.sbeBlockLength())
        .templateId(encoder.sbeTemplateId())
        .schemaId(encoder.sbeSchemaId())
        .version(encoder.sbeSchemaVersion());

    encoder.wrap(buffer, offset + headerEncoder.encodedLength());

    encoder.checksum(checksum);
  }
}
