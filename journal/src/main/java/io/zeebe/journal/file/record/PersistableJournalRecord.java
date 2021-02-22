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
import org.agrona.DirectBuffer;

/** A journal record that can be serialized in to a buffer. */
public class PersistableJournalRecord implements JournalRecord {

  final long index;
  final long asqn;
  final DirectBuffer data;
  final PersistableJournalRecordMetadata metadata = new PersistableJournalRecordMetadata();
  final PersistableJournalIndexedRecord record;

  public PersistableJournalRecord(final long index, final long asqn, final DirectBuffer data) {
    this.index = index;
    this.asqn = asqn;
    this.data = data;
    record = new PersistableJournalIndexedRecord(index, asqn, data);
  }

  public int getLength() {
    return metadata.getLength() + record.getLength();
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
  public long checksum() {
    return -1;
  }

  @Override
  public DirectBuffer data() {
    return data;
  }
}
