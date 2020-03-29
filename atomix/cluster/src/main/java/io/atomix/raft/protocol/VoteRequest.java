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
import java.util.Objects;

/**
 * Server vote request.
 *
 * <p>Vote requests are sent by candidate servers during an election to determine whether they
 * should become the leader for a cluster. Vote requests contain the necessary information for
 * followers to determine whether a candidate should receive their vote based on log and other
 * information.
 */
public class VoteRequest extends AbstractRaftRequest {

  private final long term;
  private final String candidate;
  private final long lastLogIndex;
  private final long lastLogTerm;

  public VoteRequest(
      final long term, final String candidate, final long lastLogIndex, final long lastLogTerm) {
    this.term = term;
    this.candidate = candidate;
    this.lastLogIndex = lastLogIndex;
    this.lastLogTerm = lastLogTerm;
  }

  /**
   * Returns a new vote request builder.
   *
   * @return A new vote request builder.
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
   * Returns the candidate's address.
   *
   * @return The candidate's address.
   */
  public MemberId candidate() {
    return MemberId.from(candidate);
  }

  /**
   * Returns the candidate's last log index.
   *
   * @return The candidate's last log index.
   */
  public long lastLogIndex() {
    return lastLogIndex;
  }

  /**
   * Returns the candidate's last log term.
   *
   * @return The candidate's last log term.
   */
  public long lastLogTerm() {
    return lastLogTerm;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), term, candidate, lastLogIndex, lastLogTerm);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof VoteRequest) {
      final VoteRequest request = (VoteRequest) object;
      return request.term == term
          && request.candidate == candidate
          && request.lastLogIndex == lastLogIndex
          && request.lastLogTerm == lastLogTerm;
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("term", term)
        .add("candidate", candidate)
        .add("lastLogIndex", lastLogIndex)
        .add("lastLogTerm", lastLogTerm)
        .toString();
  }

  /** Vote request builder. */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, VoteRequest> {

    private long term = -1;
    private String candidate;
    private long lastLogIndex = -1;
    private long lastLogTerm = -1;

    /**
     * Sets the request term.
     *
     * @param term The request term.
     * @return The poll request builder.
     * @throws IllegalArgumentException if {@code term} is negative
     */
    public Builder withTerm(final long term) {
      checkArgument(term >= 0, "term must be positive");
      this.term = term;
      return this;
    }

    /**
     * Sets the request leader.
     *
     * @param candidate The request candidate.
     * @return The poll request builder.
     * @throws IllegalArgumentException if {@code candidate} is not positive
     */
    public Builder withCandidate(final MemberId candidate) {
      this.candidate = checkNotNull(candidate, "candidate cannot be null").id();
      return this;
    }

    /**
     * Sets the request last log index.
     *
     * @param logIndex The request last log index.
     * @return The poll request builder.
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public Builder withLastLogIndex(final long logIndex) {
      checkArgument(logIndex >= 0, "lastLogIndex must be positive");
      this.lastLogIndex = logIndex;
      return this;
    }

    /**
     * Sets the request last log term.
     *
     * @param logTerm The request last log term.
     * @return The poll request builder.
     * @throws IllegalArgumentException if {@code term} is negative
     */
    public Builder withLastLogTerm(final long logTerm) {
      checkArgument(logTerm >= 0, "lastLogTerm must be positive");
      this.lastLogTerm = logTerm;
      return this;
    }

    @Override
    public VoteRequest build() {
      validate();
      return new VoteRequest(term, candidate, lastLogIndex, lastLogTerm);
    }

    @Override
    protected void validate() {
      super.validate();
      checkArgument(term >= 0, "term must be positive");
      checkNotNull(candidate, "candidate cannot be null");
      checkArgument(lastLogIndex >= 0, "lastLogIndex must be positive");
      checkArgument(lastLogTerm >= 0, "lastLogTerm must be positive");
    }
  }
}
