/*
 * Copyright 2015-present Open Networking Foundation
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

import io.atomix.raft.protocol.PersistedRaftRecord;
import io.atomix.raft.protocol.ReplicatedJournalRecord;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.RaftEntry;

/** Stores a state change in a {@link RaftLog}. */
public interface IndexedRaftLogEntry {

  /**
   * Returns the index of the record
   *
   * @return index
   */
  long index();

  /**
   * Returns the term of the record
   *
   * @return term
   */
  long term();

  /**
   * Return the raft entry in the record
   *
   * @return
   */
  RaftEntry entry();

  /**
   * @return true if {@code entry()} is an {@link ApplicationEntry}, otherwise false
   */
  default boolean isApplicationEntry() {
    return false;
  }

  ApplicationEntry getApplicationEntry();

  /**
   * Returns a {@link PersistedRaftRecord} that can be replicated
   *
   * @return a record to replicate
   */
  PersistedRaftRecord getPersistedRaftRecord();

  ReplicatedJournalRecord getReplicatedJournalRecord();
}
