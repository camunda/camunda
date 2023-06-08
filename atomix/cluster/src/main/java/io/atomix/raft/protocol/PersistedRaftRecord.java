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

import io.camunda.zeebe.journal.JournalRecord;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PersistedRaftRecord implements JournalRecord {

  private final long index;
  private final long asqn;
  private final long checksum;
  private final byte[] serializedRaftLogEntry;
  private final long term;

  public PersistedRaftRecord(
      final long term,
      final long index,
      final long asqn,
      final long checksum,
      final byte[] serializedRaftLogEntry) {
    this.index = index;
    this.asqn = asqn;
    this.checksum = checksum;
    this.serializedRaftLogEntry = serializedRaftLogEntry;
    this.term = term;
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
    return checksum;
  }

  @Override
  public DirectBuffer data() {
    return new UnsafeBuffer(serializedRaftLogEntry);
  }

  @Override
  public DirectBuffer serializedRecord() {
    // TODO
    return null;
  }

  /**
   * Returns the approximate size needed when serializing this class. The exact size depends on the
   * serializer.
   *
   * @return approximate size
   */
  public int approximateSize() {
    return serializedRaftLogEntry.length + Long.BYTES + Long.BYTES + Long.BYTES;
  }

  /**
   * Returns the term for this record
   *
   * @return term
   */
  public long term() {
    return term;
  }
}
