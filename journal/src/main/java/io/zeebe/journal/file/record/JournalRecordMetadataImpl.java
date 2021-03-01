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

/**
 * A {@link PersistedJournalRecord} consists of two parts. The first part is JournalRecordMetadata.
 * The second part is JournalIndexedRecord.
 *
 * <p>{@link JournalRecordMetadataImpl} is the JournalRecordMetadata which can be serialized in to a
 * buffer as part of a {@link PersistedJournalRecord}.
 */
public final class JournalRecordMetadataImpl implements JournalRecordMetadata {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final JournalRecordMetadataEncoder encoder = new JournalRecordMetadataEncoder();
  private final long checksum;
  private final int length;

  public JournalRecordMetadataImpl(final long checksum, final int recordLength) {
    this.checksum = checksum;
    length = recordLength;
  }

  @Override
  public long checksum() {
    return checksum;
  }

  @Override
  public long length() {
    return length;
  }
}
