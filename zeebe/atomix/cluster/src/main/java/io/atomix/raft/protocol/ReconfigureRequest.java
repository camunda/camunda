/*
 * Copyright 2016-present Open Networking Foundation
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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.RaftMember;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Request a change of members. Members can change type, be removed or added. */
public class ReconfigureRequest extends AbstractRaftRequest {

  private final long index;
  private final long term;
  private final Collection<RaftMember> members;

  // The sender for this request
  private final String from;

  /**
   * True if {@link #members} is a full target configuration that is only valid for the
   * configuration at exactly {@link #index}. Absent (false) in requests of members that predate
   * this field, which keep the "at least this index" semantics.
   */
  private final boolean exactIndex;

  /** Fence entries to add to the configuration; null or empty to add none. */
  private final @Nullable Collection<FenceEntry> fenced;

  /** The membership version of the target configuration. */
  private final long membershipVersion;

  /**
   * True if the request only reads the leader's configuration and changes nothing, see {@link
   * io.atomix.raft.RaftServer#readLeaderConfiguration()}. Absent (false) in requests of members
   * that predate this field.
   */
  private final boolean readOnly;

  public ReconfigureRequest(
      final Collection<RaftMember> members, final long index, final long term, final String from) {
    this(members, index, term, from, false, null, 0, false);
  }

  public ReconfigureRequest(
      final Collection<RaftMember> members,
      final long index,
      final long term,
      final String from,
      final boolean exactIndex,
      final @Nullable Collection<FenceEntry> fenced,
      final long membershipVersion,
      final boolean readOnly) {
    this.members = members;
    this.index = index;
    this.term = term;
    this.from = from;
    this.exactIndex = exactIndex;
    this.fenced = fenced;
    this.membershipVersion = membershipVersion;
    this.readOnly = readOnly;
  }

  public boolean readOnly() {
    return readOnly;
  }

  public boolean exactIndex() {
    return exactIndex;
  }

  /**
   * @return the fence entries to add, never null
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

  /**
   * Returns a new reconfigure request builder.
   *
   * @return A new reconfigure request builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the request members.
   *
   * @return The request members.
   */
  public Collection<RaftMember> members() {
    return members;
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
   * Returns the configuration term.
   *
   * @return The configuration term.
   */
  public long term() {
    return term;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), index, members);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof final ReconfigureRequest request) {
      return request.index == index && request.term == term && request.members.equals(members);
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("index", index)
        .add("term", term)
        .add("members", members)
        .add("exactIndex", exactIndex)
        .add("fenced", fenced)
        .add("membershipVersion", membershipVersion)
        .add("readOnly", readOnly)
        .toString();
  }

  @Override
  public MemberId from() {
    return MemberId.from(from);
  }

  /** Reconfigure request builder. */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, ReconfigureRequest> {

    private boolean exactIndex;
    private @Nullable Collection<FenceEntry> fenced;
    private long membershipVersion;
    private boolean readOnly;
    private Set<RaftMember> members;
    private long index = -1;
    private long term = -1;
    private String from;

    /** Fence entries to add to the configuration, see {@link FenceEntry}. */
    public Builder withFenced(final Collection<FenceEntry> fenced) {
      // Beware that immutable collections cannot be serialized by Kryo
      this.fenced = new ArrayList<>(fenced);
      return this;
    }

    /** Only reads the leader's configuration, see {@link ReconfigureRequest#readOnly()}. */
    public Builder withReadOnly(final boolean readOnly) {
      this.readOnly = readOnly;
      return this;
    }

    /** The membership version of the target configuration. */
    public Builder withMembershipVersion(final long membershipVersion) {
      this.membershipVersion = membershipVersion;
      return this;
    }

    /**
     * Requires the leader's configuration index to be exactly the request index, see {@link
     * ReconfigureRequest#exactIndex()}.
     */
    public Builder withExactIndex(final boolean exactIndex) {
      this.exactIndex = exactIndex;
      return this;
    }

    /**
     * Sets the request members.
     *
     * @param members The request members.
     * @return The request builder.
     * @throws NullPointerException if {@code members} is null
     */
    public Builder withMembers(final Collection<RaftMember> members) {
      checkNotNull(members, "members cannot be null");
      this.members = new HashSet<>(members);
      return this;
    }

    /**
     * Updates a single member.
     *
     * @param member The member to update.
     * @return The request builder.
     * @throws NullPointerException if {@code members} is null
     */
    public Builder withMember(final RaftMember member) {
      checkNotNull(member, "member cannot be null");
      members.remove(member);
      members.add(member);
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
     * Sets the request term.
     *
     * @param term The request term.
     * @return The request builder.
     */
    public Builder withTerm(final long term) {
      checkArgument(term >= 0, "term must be positive");
      this.term = term;
      return this;
    }

    /**
     * Sets the sender for this request.
     *
     * @param from Member id of the sender
     * @return The request builder.
     */
    public Builder from(final String from) {
      this.from = from;
      return this;
    }

    @Override
    public ReconfigureRequest build() {
      validate();
      return new ReconfigureRequest(
          members, index, term, from, exactIndex, fenced, membershipVersion, readOnly);
    }

    @Override
    protected void validate() {
      super.validate();
      checkNotNull(members, "members cannot be null");
      checkArgument(index >= 0, "index must be positive");
      checkArgument(term >= 0, "term must be positive");
      checkNotNull(from, "from cannot be null");
    }
  }
}
