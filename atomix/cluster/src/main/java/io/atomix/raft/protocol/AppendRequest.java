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
package io.atomix.raft.protocol;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.cluster.MemberId;
import java.util.List;
import java.util.Objects;

/**
 * Append entries request that represent old version (version = 1) which only replicated the raft
 * entry and not the complete serialized journal record.
 *
 * <p>Append entries requests are at the core of the replication protocol. Leaders send append
 * requests to followers to replicate and commit log entries, and followers sent append requests to
 * passive members to replicate committed log entries.
 */
public class AppendRequest extends AbstractRaftRequest {

  private final long term;
  private final String leader;
  private final long prevLogIndex;
  private final long prevLogTerm;
  private final List<PersistedRaftRecord> entries;
  private final long commitIndex;

  public AppendRequest(
      final long term,
      final String leader,
      final long prevLogIndex,
      final long prevLogTerm,
      final List<PersistedRaftRecord> entries,
      final long commitIndex) {
    this.term = term;
    this.leader = leader;
    this.prevLogIndex = prevLogIndex;
    this.prevLogTerm = prevLogTerm;
    this.entries = entries;
    this.commitIndex = commitIndex;
  }

  /**
   * Returns the requesting node's current term.
   *
   * @return The requesting node's current term.
   */
  public long term() {
    return term;
  }

  /**
   * Returns the requesting leader address.
   *
   * @return The leader's address.
   */
  public MemberId leader() {
    return MemberId.from(leader);
  }

  /**
   * Returns the index of the log entry preceding the new entry.
   *
   * @return The index of the log entry preceding the new entry.
   */
  public long prevLogIndex() {
    return prevLogIndex;
  }

  /**
   * Returns the term of the log entry preceding the new entry.
   *
   * @return The index of the term preceding the new entry.
   */
  public long prevLogTerm() {
    return prevLogTerm;
  }

  /**
   * Returns the log entries to append.
   *
   * @return A list of log entries.
   */
  public List<PersistedRaftRecord> entries() {
    return entries;
  }

  /**
   * Returns the leader's commit index.
   *
   * @return The leader commit index.
   */
  public long commitIndex() {
    return commitIndex;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), term, leader, prevLogIndex, prevLogTerm, entries, commitIndex);
  }

  @Override
  public boolean equals(final Object object) {
    if (object != null && object.getClass() == getClass()) {
      final AppendRequest request = (AppendRequest) object;
      return request.term == term
          && request.leader.equals(leader)
          && request.prevLogIndex == prevLogIndex
          && request.prevLogTerm == prevLogTerm
          && request.entries.equals(entries)
          && request.commitIndex == commitIndex;
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("term", term)
        .add("leader", leader)
        .add("prevLogIndex", prevLogIndex)
        .add("prevLogTerm", prevLogTerm)
        .add("entries", entries.size())
        .add("commitIndex", commitIndex)
        .toString();
  }
}
