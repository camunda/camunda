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

import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.atomix.raft.storage.serializer.RaftEntrySerializer;
import java.util.NoSuchElementException;

/** Raft log reader that reads only committed entries. */
public class RaftLogCommittedReader implements RaftLogReader {
  private final RaftLog log;
  private final RaftEntrySerializer serializer = new RaftEntrySBESerializer();

  // NOTE: nextIndex is only used if the reader is in commit mode, hence why it's not subject to
  // inconsistencies when the log is truncated/compacted/etc.
  private long nextIndex;
  private final RaftLogUncommittedReader reader;

  RaftLogCommittedReader(final RaftLog log, final RaftLogUncommittedReader reader) {
    this.log = log;
    this.reader = reader;
    nextIndex = log.getFirstIndex();
  }

  @Override
  public boolean hasNext() {
    return nextIndex <= log.getCommitIndex() && reader.hasNext();
  }

  @Override
  public IndexedRaftLogEntry next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    final IndexedRaftLogEntry entry = reader.next();

    nextIndex = entry.index() + 1;
    return entry;
  }

  public long reset() {
    nextIndex = reader.reset();
    return nextIndex;
  }

  public long seek(final long index) {
    // allow seeking one past the commit index to simulate being at the end of the log
    final long upperBoundIndex = log.getCommitIndex() + 1;
    final long boundIndex = Math.min(index, upperBoundIndex);

    nextIndex = reader.seek(boundIndex);
    return nextIndex;
  }

  public long seekToLast() {
    seek(log.getCommitIndex());
    return nextIndex;
  }

  public long seekToAsqn(final long asqn) {
    nextIndex = reader.seekToAsqn(asqn, log.getCommitIndex());
    return nextIndex;
  }

  @Override
  public void close() {
    reader.close();
  }
}
