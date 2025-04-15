/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.protocol;

import java.util.Arrays;

public record ReplicatableJournalRecord(
    long term, long index, long checksum, byte[] serializedJournalRecord)
    implements ReplicatableRaftRecord {

  // Due to having and array member, it is recommended to override equals, hashcode and toString
  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ReplicatableJournalRecord that = (ReplicatableJournalRecord) o;

    if (term != that.term) {
      return false;
    }
    if (index != that.index) {
      return false;
    }
    if (checksum != that.checksum) {
      return false;
    }
    return Arrays.equals(serializedJournalRecord, that.serializedJournalRecord);
  }

  @Override
  public int hashCode() {
    int result = (int) (term ^ (term >>> 32));
    result = 31 * result + (int) (index ^ (index >>> 32));
    result = 31 * result + (int) (checksum ^ (checksum >>> 32));
    result = 31 * result + Arrays.hashCode(serializedJournalRecord);
    return result;
  }

  @Override
  public String toString() {
    return "ReplicatableJournalRecord{"
        + "term="
        + term
        + ", index="
        + index
        + ", checksum="
        + checksum
        + ", serializedJournalRecord={rawToString="
        + serializedJournalRecord
        + ", length="
        + serializedJournalRecord.length
        + ", hashCode="
        + Arrays.hashCode(serializedJournalRecord)
        + "}"
        + '}';
  }

  /**
   * Returns the approximate size needed when serializing this class. The exact size depends on the
   * serializer.
   *
   * @return approximate size
   */
  public int approximateSize() {
    // serializedJournalRecord + index + term + checksum
    return serializedJournalRecord.length + (3 * Long.BYTES);
  }
}
