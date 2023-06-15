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
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import java.util.List;

/**
 * Append entries request that represent new versions (version > 1)
 *
 * <p>Append entries requests are at the core of the replication protocol. Leaders send append
 * requests to followers to replicate and commit log entries, and followers sent append requests to
 * passive members to replicate committed log entries.
 */
public class VersionedAppendRequest extends AbstractRaftRequest {

  private static final int CURRENT_VERSION = 2;

  private final int version;
  private final long term;
  private final String leader;
  private final long prevLogIndex;
  private final long prevLogTerm;
  private final List<ReplicatableJournalRecord> entries;
  private final long commitIndex;

  public VersionedAppendRequest(
      final int version,
      final long term,
      final String leader,
      final long prevLogIndex,
      final long prevLogTerm,
      final List<ReplicatableJournalRecord> entries,
      final long commitIndex) {
    this.version = version;
    this.term = term;
    this.leader = leader;
    this.prevLogIndex = prevLogIndex;
    this.prevLogTerm = prevLogTerm;
    this.entries = entries;
    this.commitIndex = commitIndex;
  }

  /**
   * Returns a new append request builder.
   *
   * @return A new append request builder.
   */
  public static Builder builder() {
    return new Builder();
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
  public List<ReplicatableJournalRecord> entries() {
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
    int result = version;
    result = 31 * result + (int) (term ^ (term >>> 32));
    result = 31 * result + leader.hashCode();
    result = 31 * result + (int) (prevLogIndex ^ (prevLogIndex >>> 32));
    result = 31 * result + (int) (prevLogTerm ^ (prevLogTerm >>> 32));
    result = 31 * result + entries.hashCode();
    result = 31 * result + (int) (commitIndex ^ (commitIndex >>> 32));
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final VersionedAppendRequest that = (VersionedAppendRequest) o;

    if (version != that.version) {
      return false;
    }
    if (term != that.term) {
      return false;
    }
    if (prevLogIndex != that.prevLogIndex) {
      return false;
    }
    if (prevLogTerm != that.prevLogTerm) {
      return false;
    }
    if (commitIndex != that.commitIndex) {
      return false;
    }
    if (!leader.equals(that.leader)) {
      return false;
    }
    return entries.equals(that.entries);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("version", version)
        .add("term", term)
        .add("leader", leader)
        .add("prevLogIndex", prevLogIndex)
        .add("prevLogTerm", prevLogTerm)
        .add("entries", entries.size())
        .add("commitIndex", commitIndex)
        .toString();
  }

  public int version() {
    return version;
  }

  /** Append request builder. */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, VersionedAppendRequest> {

    private static final String NULL_ENTRIES_ERR = "entries cannot be null";
    private long term;
    private String leader;
    private long logIndex;
    private long logTerm;
    private List<ReplicatableJournalRecord> entries;
    private long commitIndex = -1;
    private int version = CURRENT_VERSION;

    /**
     * Sets the request version. The default is the latest version.
     *
     * @param version The request version.
     * @return The append request builder.
     * @throws IllegalArgumentException if the {@code version} is not positive
     */
    public Builder withVersion(final int version) {
      checkArgument(version > 0, "version must be positive");
      this.version = version;
      return this;
    }

    /**
     * Sets the request term.
     *
     * @param term The request term.
     * @return The append request builder.
     * @throws IllegalArgumentException if the {@code term} is not positive
     */
    public Builder withTerm(final long term) {
      checkArgument(term > 0, "term must be positive");
      this.term = term;
      return this;
    }

    /**
     * Sets the request leader.
     *
     * @param leader The request leader.
     * @return The append request builder.
     * @throws IllegalArgumentException if the {@code leader} is not positive
     */
    public Builder withLeader(final MemberId leader) {
      this.leader = checkNotNull(leader, "leader cannot be null").id();
      return this;
    }

    /**
     * Sets the request last log index.
     *
     * @param prevLogIndex The request last log index.
     * @return The append request builder.
     * @throws IllegalArgumentException if the {@code index} is not positive
     */
    public Builder withPrevLogIndex(final long prevLogIndex) {
      checkArgument(prevLogIndex >= 0, "prevLogIndex must be positive");
      logIndex = prevLogIndex;
      return this;
    }

    /**
     * Sets the request last log term.
     *
     * @param prevLogTerm The request last log term.
     * @return The append request builder.
     * @throws IllegalArgumentException if the {@code term} is not positive
     */
    public Builder withPrevLogTerm(final long prevLogTerm) {
      checkArgument(prevLogTerm >= 0, "prevLogTerm must be positive");
      logTerm = prevLogTerm;
      return this;
    }

    /**
     * Sets the request entries.
     *
     * @param entries The request entries.
     * @return The append request builder.
     * @throws NullPointerException if {@code entries} is null
     */
    public Builder withEntries(final List<ReplicatableJournalRecord> entries) {
      this.entries = checkNotNull(entries, NULL_ENTRIES_ERR);
      return this;
    }

    /**
     * Sets the request commit index.
     *
     * @param commitIndex The request commit index.
     * @return The append request builder.
     * @throws IllegalArgumentException if index is not positive
     */
    public Builder withCommitIndex(final long commitIndex) {
      checkArgument(commitIndex >= 0, "commitIndex must be positive");
      this.commitIndex = commitIndex;
      return this;
    }

    /**
     * @throws IllegalStateException if the term, log term, log index, commit index, or global index
     *     are not positive, or if entries is null
     */
    @Override
    public VersionedAppendRequest build() {
      validate();
      return new VersionedAppendRequest(
          version, term, leader, logIndex, logTerm, entries, commitIndex);
    }

    @Override
    protected void validate() {
      super.validate();
      checkArgument(term > 0, "term must be positive");
      checkNotNull(leader, "leader cannot be null");
      checkArgument(logIndex >= 0, "prevLogIndex must be positive");
      checkArgument(logTerm >= 0, "prevLogTerm must be positive");
      checkNotNull(entries, NULL_ENTRIES_ERR);
      checkArgument(commitIndex >= 0, "commitIndex must be positive");
    }
  }
}
