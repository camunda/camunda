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
 * limitations under the License
 */
package io.atomix.raft.protocol;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Configuration installation request.
 *
 * <p>Configuration requests are special requests that aid in installing committed configurations to
 * passive and reserve members of the cluster. Prior to the start of replication from an active
 * member to a passive or reserve member, the active member must update the passive/reserve member's
 * configuration to ensure it is in the expected state.
 */
public class ConfigureRequest extends AbstractRaftRequest {

  private final long term;
  private final String leader;
  private final long index;
  private final long timestamp;
  private final Collection<RaftMember> members;
  private final Collection<RaftMember> oldMembers;
  private final long compactionBound;

  public ConfigureRequest(
      final long term,
      final String leader,
      final long index,
      final long timestamp,
      final Collection<RaftMember> newMembers,
      final Collection<RaftMember> oldMembers,
      final long compactionBound) {
    this.term = term;
    this.leader = leader;
    this.index = index;
    this.timestamp = timestamp;
    members = newMembers;
    this.oldMembers = oldMembers;
    this.compactionBound = compactionBound;
  }

  @Override
  public int hashCode() {
    return Objects.hash(term, leader, index, timestamp, members, oldMembers, compactionBound);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ConfigureRequest that = (ConfigureRequest) o;
    return term == that.term
        && index == that.index
        && timestamp == that.timestamp
        && Objects.equals(leader, that.leader)
        && Objects.equals(members, that.members)
        && Objects.equals(oldMembers, that.oldMembers)
        && compactionBound == that.compactionBound;
  }

  @Override
  public String toString() {
    return "ConfigureRequest{"
        + "term="
        + term
        + ", leader='"
        + leader
        + '\''
        + ", index="
        + index
        + ", timestamp="
        + timestamp
        + ", newMembers="
        + members
        + ", oldMembers="
        + oldMembers
        + ", compactionBound="
        + compactionBound
        + '}';
  }

  /**
   * Returns a new configuration request builder.
   *
   * @return A new configuration request builder.
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
   * Returns the configuration index.
   *
   * @return The configuration index.
   */
  public long index() {
    return index;
  }

  /**
   * Returns the configuration timestamp.
   *
   * @return The configuration timestamp.
   */
  public long timestamp() {
    return timestamp;
  }

  public long getCompactionBound() {
    return compactionBound;
  }

  /**
   * Returns the configuration members.
   *
   * @return The configuration members.
   */
  public Collection<RaftMember> newMembers() {
    return members;
  }

  /**
   * Returns the configuration members.
   *
   * @return The configuration members.
   */
  public Collection<RaftMember> oldMembers() {
    return oldMembers;
  }

  @Override
  public MemberId from() {
    return leader();
  }

  /** Heartbeat request builder. */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, ConfigureRequest> {

    private long term;
    private String leader;
    private long index;
    private long timestamp;
    private Collection<RaftMember> newMembers;
    private Collection<RaftMember> oldMembers = List.of();
    private long compactionBound;

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
     * Sets the request index.
     *
     * @param index The request index.
     * @return The request builder.
     */
    public Builder withIndex(final long index) {
      checkArgument(index >= 0, "index must be positive");
      this.index = index;
      return this;
    }

    /**
     * Sets the request timestamp.
     *
     * @param timestamp The request timestamp.
     * @return The request builder.
     */
    public Builder withTime(final long timestamp) {
      checkArgument(timestamp > 0, "timestamp must be positive");
      this.timestamp = timestamp;
      return this;
    }

    /**
     * Sets the request members.
     *
     * @param newMembers The request members.
     * @return The request builder.
     * @throws NullPointerException if {@code member} is null
     */
    public Builder withNewMembers(final Collection<RaftMember> newMembers) {
      this.newMembers = checkNotNull(newMembers, "members cannot be null");
      return this;
    }

    /**
     * Sets the request members.
     *
     * @param oldMembers The request members.
     * @return The request builder.
     * @throws NullPointerException if {@code member} is null
     */
    public Builder withOldMembers(final Collection<RaftMember> oldMembers) {
      this.oldMembers = checkNotNull(oldMembers, "members cannot be null");
      return this;
    }

    public Builder withCompactionBound(final long compactionBound) {
      this.compactionBound = compactionBound;
      return this;
    }

    /**
     * @throws IllegalStateException if member is null
     */
    @Override
    public ConfigureRequest build() {
      validate();
      return new ConfigureRequest(
          term, leader, index, timestamp, newMembers, oldMembers, compactionBound);
    }

    @Override
    protected void validate() {
      super.validate();
      checkArgument(term > 0, "term must be positive");
      checkNotNull(leader, "leader cannot be null");
      checkArgument(index >= 0, "index must be positive");
      checkArgument(timestamp > 0, "timestamp must be positive");
      checkNotNull(newMembers, "newMembers cannot be null");
      checkNotNull(oldMembers, "oldMembers cannot be null");
    }
  }
}
