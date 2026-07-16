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
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import java.util.Objects;

/**
 * Reports the terminal outcome of a coordinated leadership transfer from the current leader back to
 * the rebalancing coordinator that initiated it. Sent after an accepted {@link
 * LeadershipTransferInitiateRequest} resolves. Modelled as a request-response ack (see {@link
 * LeadershipTransferResultResponse}); the sender treats it as best-effort.
 */
public class LeadershipTransferResultRequest extends AbstractRaftRequest {

  private final MemberId leader;
  private final MemberId desiredLeader;
  private final LeadershipTransferResult result;

  private LeadershipTransferResultRequest(
      final MemberId leader, final MemberId desiredLeader, final LeadershipTransferResult result) {
    this.leader = leader;
    this.desiredLeader = desiredLeader;
    this.result = result;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The leader reporting the outcome. */
  public MemberId leader() {
    return leader;
  }

  /** The intended successor the transfer targeted. */
  public MemberId desiredLeader() {
    return desiredLeader;
  }

  /** The terminal outcome of the transfer. */
  public LeadershipTransferResult result() {
    return result;
  }

  @Override
  public MemberId from() {
    return leader;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), leader, desiredLeader, result);
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || !getClass().isAssignableFrom(object.getClass())) {
      return false;
    }
    final LeadershipTransferResultRequest other = (LeadershipTransferResultRequest) object;
    return result == other.result
        && leader.equals(other.leader)
        && desiredLeader.equals(other.desiredLeader);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("leader", leader)
        .add("desiredLeader", desiredLeader)
        .add("result", result)
        .toString();
  }

  /** Leadership-transfer result request builder. */
  public static class Builder
      extends AbstractRaftRequest.Builder<Builder, LeadershipTransferResultRequest> {

    private MemberId leader;
    private MemberId desiredLeader;
    private LeadershipTransferResult result;

    public Builder withLeader(final MemberId leader) {
      this.leader = checkNotNull(leader, "leader cannot be null");
      return this;
    }

    public Builder withDesiredLeader(final MemberId desiredLeader) {
      this.desiredLeader = checkNotNull(desiredLeader, "desiredLeader cannot be null");
      return this;
    }

    public Builder withResult(final LeadershipTransferResult result) {
      this.result = checkNotNull(result, "result cannot be null");
      return this;
    }

    @Override
    protected void validate() {
      super.validate();
      checkNotNull(leader, "leader cannot be null");
      checkNotNull(desiredLeader, "desiredLeader cannot be null");
      checkNotNull(result, "result cannot be null");
    }

    @Override
    public LeadershipTransferResultRequest build() {
      validate();
      return new LeadershipTransferResultRequest(leader, desiredLeader, result);
    }
  }
}
