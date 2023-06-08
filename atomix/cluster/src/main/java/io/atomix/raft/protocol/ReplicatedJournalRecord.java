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

public class ReplicatedJournalRecord implements ReplicatedRecord {

  private final long index;
  private final long checksum;
  private final byte[] serializedJournalRecord;
  private final long term;

  public ReplicatedJournalRecord(
      final long term,
      final long index,
      final long checksum,
      final byte[] serializedJournalRecord) {
    this.index = index;
    this.checksum = checksum;
    this.serializedJournalRecord = serializedJournalRecord;
    this.term = term;
  }

  @Override
  public long index() {
    return index;
  }

  /**
   * Returns the term for this record
   *
   * @return term
   */
  @Override
  public long term() {
    return term;
  }

  public long checksum() {
    return checksum;
  }

  public byte[] serializedJournalRecord() {
    return serializedJournalRecord;
  }

  /**
   * Returns the approximate size needed when serializing this class. The exact size depends on the
   * serializer.
   *
   * @return approximate size
   */
  public int approximateSize() {
    return serializedJournalRecord.length + Long.BYTES + Long.BYTES;
  }
}
