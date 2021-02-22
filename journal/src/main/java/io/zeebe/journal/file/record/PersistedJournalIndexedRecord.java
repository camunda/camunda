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

import io.zeebe.journal.file.JournalIndexedRecordDecoder;
import io.zeebe.journal.file.MessageHeaderDecoder;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A {@link PersistedJournalRecord} consists of two parts. The first part is JournalRecordMetadata.
 * The second part is JournalIndexedRecord.
 *
 * <p>{@link PersistedJournalIndexedRecord} is the JournalIndexedRecord which is serialized in to a
 * buffer as part of a {@link PersistedJournalRecord}.
 */
final class PersistedJournalIndexedRecord {
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final JournalIndexedRecordDecoder decoder = new JournalIndexedRecordDecoder();
  private final DirectBuffer data = new UnsafeBuffer();

  public PersistedJournalIndexedRecord(final DirectBuffer buffer) {
    wrap(buffer);
    decoder.wrapApplicationRecord(data);
  }

  public long index() {
    return decoder.index();
  }

  public long asqn() {
    return decoder.asqn();
  }

  public DirectBuffer data() {
    return data;
  }

  public int getLength() {
    return headerDecoder.encodedLength()
        + headerDecoder.blockLength()
        + JournalIndexedRecordDecoder.applicationRecordHeaderLength()
        + data.capacity();
  }

  public void wrap(final DirectBuffer buffer) {
    wrap(buffer, 0, buffer.capacity());
  }

  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    if (!canRead(buffer, 0)) {
      throw new InvalidRecord("Cannot read buffer. Header does not match.");
    }
    headerDecoder.wrap(buffer, offset);
    decoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());
  }

  public boolean canRead(final DirectBuffer buffer, final int offset) {
    headerDecoder.wrap(buffer, offset);
    return (headerDecoder.schemaId() == decoder.sbeSchemaId()
        && headerDecoder.templateId() == decoder.sbeTemplateId());
  }
}
