/*
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

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RaftError;
import java.util.Objects;

/**
 * Immediate acknowledgement of a {@link LeadershipTransferInitiateRequest}.
 *
 * <p>If {@code accepted} is true the current leader has taken the transfer on and will report the
 * terminal outcome later via a {@link LeadershipTransferResultRequest}. If {@code accepted} is
 * false the transfer was resolved immediately: {@code result} carries the skip reason (a failed
 * pre-check) and {@code leader} points to the node the receiver believes is the leader, so a
 * request that reached a follower can be redirected.
 */
public class LeadershipTransferInitiateResponse extends AbstractRaftResponse {

  private final boolean accepted;
  private final LeadershipTransferResult result;
  private final MemberId leader;

  public LeadershipTransferInitiateResponse(
      final Status status,
      final RaftError error,
      final boolean accepted,
      final LeadershipTransferResult result,
      final MemberId leader) {
    super(status, error);
    this.accepted = accepted;
    this.result = result;
    this.leader = leader;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Whether the leader accepted the transfer and will report the outcome asynchronously. */
  public boolean accepted() {
    return accepted;
  }

  /** The immediate skip result when the transfer was not accepted, or {@code null} otherwise. */
  public LeadershipTransferResult result() {
    return result;
  }

  /** The node the receiver believes is the leader, for redirecting a misrouted request. */
  public MemberId leader() {
    return leader;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), status, accepted, result, leader);
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || !getClass().isAssignableFrom(object.getClass())) {
      return false;
    }
    final LeadershipTransferInitiateResponse other = (LeadershipTransferInitiateResponse) object;
    return status == other.status
        && accepted == other.accepted
        && result == other.result
        && Objects.equals(leader, other.leader);
  }

  @Override
  public String toString() {
    if (status == Status.OK) {
      return toStringHelper(this)
          .add("status", status)
          .add("accepted", accepted)
          .add("result", result)
          .add("leader", leader)
          .toString();
    }
    return toStringHelper(this).add("status", status).add("error", error).toString();
  }

  /** Leadership-transfer initiate response builder. */
  public static class Builder
      extends AbstractRaftResponse.Builder<Builder, LeadershipTransferInitiateResponse> {

    private boolean accepted;
    private LeadershipTransferResult result;
    private MemberId leader;

    public Builder withAccepted(final boolean accepted) {
      this.accepted = accepted;
      return this;
    }

    public Builder withResult(final LeadershipTransferResult result) {
      this.result = result;
      return this;
    }

    public Builder withLeader(final MemberId leader) {
      this.leader = leader;
      return this;
    }

    @Override
    public LeadershipTransferInitiateResponse build() {
      validate();
      return new LeadershipTransferInitiateResponse(status, error, accepted, result, leader);
    }
  }
}
