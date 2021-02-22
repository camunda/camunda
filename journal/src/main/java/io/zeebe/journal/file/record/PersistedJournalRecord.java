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
import java.nio.ByteBuffer;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A JournalRecord stored in a buffer.
 *
 * <p>A {@link PersistedJournalRecord} consists of two parts. The first part is {@link
 * PersistedJournalRecordMetadata}. The second part is {@link PersistedJournalIndexedRecord}.
 */
public class PersistedJournalRecord implements JournalRecord {
  final PersistedJournalRecordMetadata metadata;
  final PersistedJournalIndexedRecord record;

  public PersistedJournalRecord(final ByteBuffer buffer) {
    final var slice = buffer.slice();
    metadata = new PersistedJournalRecordMetadata(new UnsafeBuffer(slice));
    final var metadataLength = metadata.getLength();
    slice.position(metadataLength);
    record = new PersistedJournalIndexedRecord(new UnsafeBuffer(slice.slice()));
  }

  public int getMetadataLength() {
    return metadata.getLength();
  }

  public int getIndexedRecordLength() {
    return record.getLength();
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

  public int getLength() {
    return metadata.getLength() + record.getLength();
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
    final PersistedJournalRecord that = (PersistedJournalRecord) o;
    return that.index() == index()
        && that.asqn() == asqn()
        && that.checksum() == checksum()
        && Objects.equals(that.data(), data());
  }
}
