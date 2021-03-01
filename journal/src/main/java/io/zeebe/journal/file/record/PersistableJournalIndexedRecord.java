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

import io.zeebe.journal.file.JournalIndexedRecordEncoder;
import io.zeebe.journal.file.MessageHeaderEncoder;
import org.agrona.DirectBuffer;

/**
 * A {@link PersistedJournalRecord} consists of two parts. The first part is JournalRecordMetadata.
 * The second part is JournalIndexedRecord.
 *
 * <p>{@link PersistableJournalIndexedRecord} is the JournalRecordMetadata which can be serialized
 * in to a buffer as part of a {@link PersistedJournalRecord}.
 */
public class PersistableJournalIndexedRecord implements JournalIndexedRecord {

  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final JournalIndexedRecordEncoder encoder = new JournalIndexedRecordEncoder();
  private final long index;
  private final long asqn;
  private final DirectBuffer data;

  public PersistableJournalIndexedRecord(
      final long index, final long asqn, final DirectBuffer data) {
    this.index = index;
    this.asqn = asqn;
    this.data = data;
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public long asqn() {
    return asqn;
  }

  @Override
  public DirectBuffer data() {
    return data;
  }
}
