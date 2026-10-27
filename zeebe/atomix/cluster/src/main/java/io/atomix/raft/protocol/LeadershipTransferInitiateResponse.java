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

import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RaftError;
import java.util.Objects;

/**
 * Immediate acknowledgement of a {@link LeadershipTransferInitiateRequest}.
 *
 * <p>An {@code OK} response comes from the leader. A null {@code rejectionReason} means it has
 * accepted the transfer request and will report the terminal outcome later via a {@link
 * LeadershipTransferResultRequest}; otherwise the rejection reason resolves the request
 * immediately.
 *
 * <p>A receiver that is not the leader answers {@code ERROR} with {@link
 * RaftError.Type#ILLEGAL_MEMBER_STATE}, like any other misrouted Raft request.
 */
public class LeadershipTransferInitiateResponse extends AbstractRaftResponse {

  private final LeadershipTransferResult rejectionReason;

  public LeadershipTransferInitiateResponse(
      final Status status, final RaftError error, final LeadershipTransferResult rejectionReason) {
    super(status, error);
    this.rejectionReason = rejectionReason;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Whether the leader took the transfer on and will report the outcome asynchronously. */
  public boolean accepted() {
    return status == Status.OK && rejectionReason == null;
  }

  /**
   * The pre-check that resolved the transfer immediately, or {@code null} if the leader accepted
   * it. Always {@code null} when {@code status} is not {@code OK}.
   */
  public LeadershipTransferResult rejectionReason() {
    return rejectionReason;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), status, rejectionReason);
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
        && rejectionReason == other.rejectionReason
        && Objects.equals(error, other.error);
  }

  @Override
  public String toString() {
    if (status == Status.OK) {
      return toStringHelper(this)
          .add("status", status)
          .add("rejectionReason", rejectionReason)
          .toString();
    }
    return toStringHelper(this).add("status", status).add("error", error).toString();
  }

  /** Leadership-transfer initiate response builder. */
  public static class Builder
      extends AbstractRaftResponse.Builder<Builder, LeadershipTransferInitiateResponse> {

    private LeadershipTransferResult rejectionReason;

    public Builder withRejectionReason(final LeadershipTransferResult rejectionReason) {
      this.rejectionReason = rejectionReason;
      return this;
    }

    @Override
    public LeadershipTransferInitiateResponse build() {
      validate();
      return new LeadershipTransferInitiateResponse(status, error, rejectionReason);
    }
  }
}
