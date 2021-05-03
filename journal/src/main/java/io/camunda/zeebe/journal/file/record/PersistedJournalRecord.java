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
import java.util.Objects;
import org.agrona.DirectBuffer;

/**
 * A JournalRecord stored in a buffer.
 *
 * <p>A {@link PersistedJournalRecord} consists of two parts. The first part is {@link
 * RecordMetadata}. The second part is {@link RecordData}.
 */
public class PersistedJournalRecord implements JournalRecord {
  private final RecordMetadata metadata;
  private final RecordData record;

  public PersistedJournalRecord(final RecordMetadata metadata, final RecordData record) {
    this.metadata = metadata;
    this.record = record;
  }

  @Override
  public long index() {
    return record.index();
  }

  @Override
  public long asqn() {
    return record.asqn();
  }

  @Override
  public long checksum() {
    return metadata.checksum();
  }

  @Override
  public DirectBuffer data() {
    return record.data();
  }

  @Override
  public int hashCode() {
    return Objects.hash(record);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JournalRecord that = (JournalRecord) o;
    return that.index() == index()
        && that.asqn() == asqn()
        && that.checksum() == checksum()
        && Objects.equals(that.data(), data());
  }

  @Override
  public String toString() {
    return "PersistedJournalRecord{" + "metadata=" + metadata + ", record=" + record + '}';
  }
}
