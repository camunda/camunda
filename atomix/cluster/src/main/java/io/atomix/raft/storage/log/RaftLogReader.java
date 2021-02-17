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
import io.atomix.storage.journal.Indexed;
import io.atomix.storage.journal.JournalReader;

/** Raft log reader. */
public class RaftLogReader implements java.util.Iterator<Indexed<RaftLogEntry>>, AutoCloseable {
  private final JournalReader<RaftLogEntry> delegate;

  RaftLogReader(final JournalReader<RaftLogEntry> delegate) {
    this.delegate = delegate;
  }

  public long getFirstIndex() {
    return delegate.getFirstIndex();
  }

  public long getLastIndex() {
    return delegate.getLastIndex();
  }

  @Override
  public boolean hasNext() {
    return delegate.hasNext();
  }

  @Override
  public Indexed<RaftLogEntry> next() {
    return delegate.next();
  }

  public void reset() {
    delegate.reset();
  }

  public void reset(final long index) {
    delegate.reset(index);
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public String toString() {
    return "RaftLogReader{" + "delegate=" + delegate + '}';
  }

  /** Raft log reader mode. */
  public enum Mode {

    /** Reads all entries from the log. */
    ALL,

    /** Reads committed entries from the log. */
    COMMITS,
  }
}
