/*
 * Copyright 2015-present Open Networking Foundation
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import java.util.Objects;

public class LeaderHeartbeatRequest extends AbstractRaftRequest {

  private final long term;
  private final String leader;
  private final long commitIndex;

  public LeaderHeartbeatRequest(final long term, final String leader, final long commitIndex) {
    this.term = term;
    this.leader = leader;
    this.commitIndex = commitIndex;
  }

  /**
   * Returns a new append request builder.
   *
   * @return A new append request builder.
   */
  public static LeaderHeartbeatRequest.Builder builder() {
    return new LeaderHeartbeatRequest.Builder();
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
   * Returns the leader's commit index.
   *
   * @return The leader commit index.
   */
  public long commitIndex() {
    return commitIndex;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), term, leader, commitIndex);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final LeaderHeartbeatRequest that = (LeaderHeartbeatRequest) o;
    return term == that.term && commitIndex == that.commitIndex && leader.equals(that.leader);
  }

  @Override
  public String toString() {
    return "LeaderHeartbeatRequest{"
        + "term="
        + term
        + ", leader='"
        + leader
        + '\''
        + ", commitIndex="
        + commitIndex
        + '}';
  }

  /** Append request builder. */
  public static class Builder
      extends AbstractRaftRequest.Builder<LeaderHeartbeatRequest.Builder, LeaderHeartbeatRequest> {

    private long term;
    private String leader;
    private long commitIndex = -1;

    /**
     * Sets the request term.
     *
     * @param term The request term.
     * @return The append request builder.
     * @throws IllegalArgumentException if the {@code term} is not positive
     */
    public LeaderHeartbeatRequest.Builder withTerm(final long term) {
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
    public LeaderHeartbeatRequest.Builder withLeader(final MemberId leader) {
      this.leader = checkNotNull(leader, "leader cannot be null").id();
      return this;
    }

    /**
     * Sets the request commit index.
     *
     * @param commitIndex The request commit index.
     * @return The append request builder.
     * @throws IllegalArgumentException if index is not positive
     */
    public LeaderHeartbeatRequest.Builder withCommitIndex(final long commitIndex) {
      checkArgument(commitIndex >= 0, "commitIndex must be positive");
      this.commitIndex = commitIndex;
      return this;
    }

    /**
     * @throws IllegalStateException if the term, log term, log index, commit index, or global index
     *     are not positive, or if entries is null
     */
    @Override
    public LeaderHeartbeatRequest build() {
      validate();
      return new LeaderHeartbeatRequest(term, leader, commitIndex);
    }

    @Override
    protected void validate() {
      super.validate();
      checkArgument(term > 0, "term must be positive");
      checkNotNull(leader, "leader cannot be null");
      checkArgument(commitIndex >= 0, "commitIndex must be positive");
    }
  }
}
