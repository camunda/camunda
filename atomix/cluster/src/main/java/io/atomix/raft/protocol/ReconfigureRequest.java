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

import io.atomix.raft.cluster.RaftMember;
import io.atomix.utils.Builder;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Request a change of members. Members can change type, be removed or added. */
public class ReconfigureRequest extends AbstractRaftRequest {

  private final long index;
  private final long term;
  private final Collection<RaftMember> members;

  public ReconfigureRequest(
      final Collection<RaftMember> members, final long index, final long term) {
    this.members = members;
    this.index = index;
    this.term = term;
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
        .toString();
  }

  /** Reconfigure request builder. */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, ReconfigureRequest> {

    private Set<RaftMember> members;
    private long index = -1;
    private long term = -1;

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

    @Override
    public ReconfigureRequest build() {
      validate();
      return new ReconfigureRequest(members, index, term);
    }

    @Override
    protected void validate() {
      super.validate();
      checkNotNull(members, "members cannot be null");
      checkArgument(index >= 0, "index must be positive");
      checkArgument(term >= 0, "term must be positive");
    }
  }
}
