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

/** Server append entries response. */
public class AppendResponse extends AbstractRaftResponse {

  private final long term;
  private final boolean succeeded;
  private final long lastLogIndex;
  private final long lastSnapshotIndex;

  public AppendResponse(
      final Status status,
      final RaftError error,
      final long term,
      final boolean succeeded,
      final long lastLogIndex,
      final long lastSnapshotIndex) {
    super(status, error);
    this.term = term;
    this.succeeded = succeeded;
    this.lastLogIndex = lastLogIndex;
    this.lastSnapshotIndex = lastSnapshotIndex;
  }

  /**
   * Returns a new append response builder.
   *
   * @return A new append response builder.
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
   * Returns a boolean indicating whether the append was successful.
   *
   * @return Indicates whether the append was successful.
   */
  public boolean succeeded() {
    return succeeded;
  }

  /**
   * Returns the last index of the replica's log.
   *
   * @return The last index of the responding replica's log.
   */
  public long lastLogIndex() {
    return lastLogIndex;
  }

  /**
   * Returns the index of the replica's last snapshot
   *
   * @return The index of the responding replica's last snapshot
   */
  public long lastSnapshotIndex() {
    return lastSnapshotIndex;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), status, term, succeeded, lastLogIndex, lastSnapshotIndex);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof AppendResponse) {
      final AppendResponse response = (AppendResponse) object;
      return response.status == status
          && response.term == term
          && response.succeeded == succeeded
          && response.lastLogIndex == lastLogIndex
          && response.lastSnapshotIndex == lastSnapshotIndex;
    }
    return false;
  }

  @Override
  public String toString() {
    if (status == Status.OK) {
      return toStringHelper(this)
          .add("status", status)
          .add("term", term)
          .add("succeeded", succeeded)
          .add("lastLogIndex", lastLogIndex)
          .add("lastSnapshotIndex", lastSnapshotIndex)
          .toString();
    } else {
      return toStringHelper(this).add("status", status).add("error", error).toString();
    }
  }

  /** Append response builder. */
  public static class Builder extends AbstractRaftResponse.Builder<Builder, AppendResponse> {

    private long term;
    private boolean succeeded;
    private long lastLogIndex;
    private long lastSnapshotIndex;

    /**
     * Sets the response term.
     *
     * @param term The response term.
     * @return The append response builder
     * @throws IllegalArgumentException if {@code term} is not positive
     */
    public Builder withTerm(final long term) {
      checkArgument(term > 0, "term must be positive");
      this.term = term;
      return this;
    }

    /**
     * Sets whether the request succeeded.
     *
     * @param succeeded Whether the append request succeeded.
     * @return The append response builder.
     */
    public Builder withSucceeded(final boolean succeeded) {
      this.succeeded = succeeded;
      return this;
    }

    /**
     * Sets the last index of the replica's log.
     *
     * @param lastLogIndex The last index of the replica's log.
     * @return The append response builder.
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public Builder withLastLogIndex(final long lastLogIndex) {
      checkArgument(lastLogIndex >= 0, "lastLogIndex must be positive");
      this.lastLogIndex = lastLogIndex;
      return this;
    }

    public Builder withLastSnapshotIndex(final long lastSnapshotIndex) {
      checkArgument(lastSnapshotIndex >= 0, "lastSnapshotIndex must be positive");
      this.lastSnapshotIndex = lastSnapshotIndex;
      return this;
    }

    /**
     * @throws IllegalStateException if status is ok and term is not positive or log index is
     *     negative
     */
    @Override
    public AppendResponse build() {
      validate();
      return new AppendResponse(status, error, term, succeeded, lastLogIndex, lastSnapshotIndex);
    }

    @Override
    protected void validate() {
      super.validate();
      if (status == Status.OK) {
        checkArgument(term > 0, "term must be positive");
        checkArgument(lastLogIndex >= 0, "lastLogIndex must be positive");
        checkArgument(lastSnapshotIndex >= 0, "lastSnapshotIndex must be positive");
      }
    }
  }
}
