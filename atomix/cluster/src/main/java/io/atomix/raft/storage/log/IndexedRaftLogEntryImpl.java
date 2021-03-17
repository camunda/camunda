/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.storage.log;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.RaftEntry;
import io.zeebe.journal.JournalRecord;
import java.util.Objects;

/** Indexed journal entry. */
class IndexedRaftLogEntryImpl implements IndexedRaftLogEntry {

  private final long index;
  private final long term;
  private final RaftEntry entry;
  private final JournalRecord record;

  public IndexedRaftLogEntryImpl(
      final long term, final RaftEntry entry, final JournalRecord record) {
    this.term = term;
    this.entry = entry;
    this.record = record;
    index = record.index();
  }

  @Override
  public long index() {
    return record.index();
  }

  @Override
  public long term() {
    return term;
  }

  @Override
  public RaftEntry entry() {
    return entry;
  }

  @Override
  public boolean isApplicationEntry() {
    return entry instanceof ApplicationEntry;
  }

  @Override
  public ApplicationEntry getApplicationEntry() {
    return (ApplicationEntry) entry;
  }

  @Override
  public PersistedRaftRecord getPersistedRaftRecord() {
    final byte[] serializedRaftLogEntry = new byte[record.data().capacity()];
    record.data().getBytes(0, serializedRaftLogEntry);
    return new PersistedRaftRecord(
        term, index, record.asqn(), record.checksum(), serializedRaftLogEntry);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, term, entry, record);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IndexedRaftLogEntryImpl that = (IndexedRaftLogEntryImpl) o;
    return index == that.index
        && term == that.term
        && record.asqn() == that.record.asqn()
        && record.checksum() == that.record.checksum()
        && Objects.equals(entry, that.entry);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("index", index)
        .add("term", term)
        .add("entry", entry)
        .add("record", record)
        .toString();
  }
}
