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

import io.atomix.raft.RaftError;
import java.util.Objects;

/**
 * Server vote response.
 *
 * <p>Vote responses are sent by active servers in response to vote requests by candidate to
 * indicate whether the responding server voted for the requesting candidate. This is indicated by
 * the {@link #voted()} field of the response.
 */
public class VoteResponse extends AbstractRaftResponse {

  private final long term;
  private final boolean voted;

  public VoteResponse(
      final Status status, final RaftError error, final long term, final boolean voted) {
    super(status, error);
    this.term = term;
    this.voted = voted;
  }

  /**
   * Returns a new vote response builder.
   *
   * @return A new vote response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the responding node's current term.
   *
   * @return The responding node's current term.
   */
  public long term() {
    return term;
  }

  /**
   * Returns a boolean indicating whether the vote was granted.
   *
   * @return Indicates whether the vote was granted.
   */
  public boolean voted() {
    return voted;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), status, term, voted);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof VoteResponse) {
      final VoteResponse response = (VoteResponse) object;
      return response.status == status && response.term == term && response.voted == voted;
    }
    return false;
  }

  @Override
  public String toString() {
    if (status == Status.OK) {
      return toStringHelper(this)
          .add("status", status)
          .add("term", term)
          .add("voted", voted)
          .toString();
    } else {
      return toStringHelper(this).add("status", status).add("error", error).toString();
    }
  }

  /** Poll response builder. */
  public static class Builder extends AbstractRaftResponse.Builder<Builder, VoteResponse> {

    private long term = -1;
    private boolean voted;

    /**
     * Sets the response term.
     *
     * @param term The response term.
     * @return The vote response builder.
     * @throws IllegalArgumentException if {@code term} is negative
     */
    public Builder withTerm(final long term) {
      checkArgument(term >= 0, "term must be positive");
      this.term = term;
      return this;
    }

    /**
     * Sets whether the vote was granted.
     *
     * @param voted Whether the vote was granted.
     * @return The vote response builder.
     */
    public Builder withVoted(final boolean voted) {
      this.voted = voted;
      return this;
    }

    @Override
    public VoteResponse build() {
      validate();
      return new VoteResponse(status, error, term, voted);
    }

    @Override
    protected void validate() {
      super.validate();
      if (status == Status.OK) {
        checkArgument(term >= 0, "term must be positive");
      }
    }
  }
}
