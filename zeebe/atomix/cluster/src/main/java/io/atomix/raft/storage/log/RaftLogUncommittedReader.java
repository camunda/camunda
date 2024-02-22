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

import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.atomix.raft.storage.serializer.RaftEntrySerializer;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import java.util.NoSuchElementException;

/**
 * Raft log reader that reads both committed and uncommitted entries. This reader is supposed to be
 * only used internally in raft. The reader is not thread safe with a concurrent writer.
 */
public class RaftLogUncommittedReader implements RaftLogReader {
  private final JournalReader journalReader;
  private final RaftEntrySerializer serializer = new RaftEntrySBESerializer();

  RaftLogUncommittedReader(final JournalReader journalReader) {
    this.journalReader = journalReader;
  }

  @Override
  public boolean hasNext() {
    return journalReader.hasNext();
  }

  @Override
  public IndexedRaftLogEntry next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    final JournalRecord journalRecord = journalReader.next();
    final RaftLogEntry entry = serializer.readRaftLogEntry(journalRecord.data());

    return new IndexedRaftLogEntryImpl(entry.term(), entry.entry(), journalRecord);
  }

  @Override
  public long reset() {
    return journalReader.seekToFirst();
  }

  @Override
  public long seek(final long index) {
    return journalReader.seek(index);
  }

  @Override
  public long seekToLast() {
    return journalReader.seekToLast();
  }

  @Override
  public long seekToAsqn(final long asqn) {
    return journalReader.seekToAsqn(asqn);
  }

  @Override
  public void close() {
    journalReader.close();
  }

  public long seekToAsqn(final long asqn, final long indexUpperBound) {
    return journalReader.seekToAsqn(asqn, indexUpperBound);
  }
}
