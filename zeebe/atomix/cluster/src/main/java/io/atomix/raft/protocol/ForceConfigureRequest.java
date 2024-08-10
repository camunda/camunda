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
import java.util.Objects;
import java.util.Set;

/**
 * Request to change configuration forcefully without going through the joint consensus.
 *
 * <p>Unlike the {@link ConfigureRequest}, this request is not sent from a leader. A force
 * configuration is requested typically to remove a set of (unavailable) members when a quorum is
 * not possible without them.
 */
public class ForceConfigureRequest extends AbstractRaftRequest {

  private final long term;
  private final long index;
  private final long timestamp;
  private final Set<RaftMember> members;
  private final String from;

  public ForceConfigureRequest(
      final long term,
      final long index,
      final long timestamp,
      final Set<RaftMember> newMembers,
      final String from) {
    this.term = term;
    this.index = index;
    this.timestamp = timestamp;
    members = newMembers;
    this.from = from;
  }

  @Override
  public int hashCode() {
    return Objects.hash(term, index, timestamp, members, from);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForceConfigureRequest that = (ForceConfigureRequest) o;
    return term == that.term
        && index == that.index
        && timestamp == that.timestamp
        && Objects.equals(members, that.members)
        && Objects.equals(from, that.from);
  }

  @Override
  public String toString() {
    return "ForceConfigureRequest{"
        + "term="
        + term
        + ", index="
        + index
        + ", timestamp="
        + timestamp
        + ", members="
        + members
        + ", from='"
        + from
        + '\''
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

  /**
   * Returns the configuration members.
   *
   * @return The configuration members.
   */
  public Collection<RaftMember> newMembers() {
    return members;
  }

  @Override
  public MemberId from() {
    return MemberId.from(from);
  }

  /** Heartbeat request builder. */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, ForceConfigureRequest> {

    private long term;
    private long index;
    private long timestamp;
    private Set<RaftMember> newMembers;
    private String from;

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
     * Sets the members that will be part of the new configuration
     *
     * @param newMembers members in the new configuration.
     * @return The request builder.
     * @throws NullPointerException if {@code member} is null
     */
    public Builder withNewMembers(final Set<RaftMember> newMembers) {
      this.newMembers = checkNotNull(newMembers, "members cannot be null");
      return this;
    }

    public Builder from(final MemberId from) {
      this.from = from.id();
      return this;
    }

    /**
     * @throws IllegalStateException if member is null
     */
    @Override
    public ForceConfigureRequest build() {
      validate();
      return new ForceConfigureRequest(term, index, timestamp, newMembers, from);
    }

    @Override
    protected void validate() {
      super.validate();
      checkArgument(term > 0, "term must be positive");
      checkArgument(index >= 0, "index must be positive");
      checkArgument(timestamp > 0, "timestamp must be positive");
      checkNotNull(newMembers, "newMembers cannot be null");
      checkNotNull(from, "sender id cannot be null");
    }
  }
}
