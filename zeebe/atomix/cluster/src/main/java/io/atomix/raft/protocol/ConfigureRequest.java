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
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.RaftMember;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

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

  /**
   * The term of the configuration entry at {@link #index}, which together with the index identifies
   * the configuration - as opposed to {@link #term}, the leader's current term, which only says who
   * is disseminating it. Absent (zero) from requests built by members that predate this field; a
   * receiver then has to fall back to {@link #term}, which is what made {@code
   * Configuration#term()} ambiguous in the first place.
   */
  private final long configurationTerm;

  private final Collection<RaftMember> members;
  private final Collection<RaftMember> oldMembers;

  // Absent (null) in requests of members that predate fencing and voter policies.
  private final @Nullable Collection<FenceEntry> fenced;
  private final long membershipVersion;

  /**
   * True if the leader has committed this configuration. A member acts on finding itself on the
   * fence list only in a committed configuration (see {@link FenceEntry}).
   */
  private final boolean committed;

  public ConfigureRequest(
      final long term,
      final String leader,
      final long index,
      final long timestamp,
      final Collection<RaftMember> newMembers,
      final Collection<RaftMember> oldMembers,
      final long configurationTerm) {
    this(term, leader, index, timestamp, newMembers, oldMembers, configurationTerm, null, 0, false);
  }

  public ConfigureRequest(
      final long term,
      final String leader,
      final long index,
      final long timestamp,
      final Collection<RaftMember> newMembers,
      final Collection<RaftMember> oldMembers,
      final long configurationTerm,
      final @Nullable Collection<FenceEntry> fenced,
      final long membershipVersion,
      final boolean committed) {
    this.term = term;
    this.leader = leader;
    this.index = index;
    this.timestamp = timestamp;
    members = newMembers;
    this.oldMembers = oldMembers;
    this.configurationTerm = configurationTerm;
    this.fenced = fenced;
    this.membershipVersion = membershipVersion;
    this.committed = committed;
  }

  /**
   * @return the fence list of the configuration, never null
   */
  public Collection<FenceEntry> fenced() {
    return fenced == null ? List.of() : fenced;
  }

  /**
   * @return the membership version of the configuration, see {@link
   *     io.atomix.raft.storage.system.Configuration#membershipVersion()}
   */
  public long membershipVersion() {
    return membershipVersion;
  }

  public boolean committed() {
    return committed;
  }

  @Override
  public int hashCode() {
    return Objects.hash(term, leader, index, timestamp, members, oldMembers, configurationTerm);
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
        && configurationTerm == that.configurationTerm
        && Objects.equals(leader, that.leader)
        && Objects.equals(members, that.members)
        && Objects.equals(oldMembers, that.oldMembers);
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
        + ", configurationTerm="
        + configurationTerm
        + ", fenced="
        + fenced
        + ", membershipVersion="
        + membershipVersion
        + ", committed="
        + committed
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
   * Returns the term of the configuration entry at {@link #index()}, or {@code 0} when the sender
   * does not provide it.
   *
   * @return The configuration's term, or {@code 0} if unknown.
   */
  public long configurationTerm() {
    return configurationTerm;
  }

  /**
   * Returns the configuration timestamp.
   *
   * @return The configuration timestamp.
   */
  public long timestamp() {
    return timestamp;
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
    private long configurationTerm;
    private String leader;
    private long index;
    private long timestamp;
    private Collection<RaftMember> newMembers;
    private Collection<RaftMember> oldMembers = List.of();
    private boolean committed;

    private @Nullable Collection<FenceEntry> fenced;
    private long membershipVersion;

    public Builder withFenced(final Collection<FenceEntry> fenced) {
      // Beware that immutable collections cannot be serialized by Kryo
      this.fenced = new ArrayList<>(fenced);
      return this;
    }

    public Builder withMembershipVersion(final long membershipVersion) {
      this.membershipVersion = membershipVersion;
      return this;
    }

    public Builder withCommitted(final boolean committed) {
      this.committed = committed;
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
     * Sets the term of the configuration entry at the request index.
     *
     * @param configurationTerm The configuration entry's term.
     * @return The request builder.
     */
    public Builder withConfigurationTerm(final long configurationTerm) {
      checkArgument(configurationTerm >= 0, "configurationTerm must not be negative");
      this.configurationTerm = configurationTerm;
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

    /**
     * @throws IllegalStateException if member is null
     */
    @Override
    public ConfigureRequest build() {
      validate();
      return new ConfigureRequest(
          term,
          leader,
          index,
          timestamp,
          newMembers,
          oldMembers,
          configurationTerm,
          fenced,
          membershipVersion,
          committed);
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
