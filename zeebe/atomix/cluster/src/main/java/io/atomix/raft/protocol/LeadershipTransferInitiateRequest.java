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
import java.util.Objects;

/**
 * Initiate request for a coordinated leadership transfer.
 *
 * <p>Sent by the rebalancing coordinator to a partition's current leader to ask it to hand
 * leadership to {@code desiredLeader}. The leader validates the request (see the pre-checks), and
 * either rejects it immediately (returning a skip result in the {@link
 * LeadershipTransferInitiateResponse}) or accepts it and drives the transfer, reporting the
 * terminal outcome asynchronously via a {@link LeadershipTransferResultRequest}. The {@code
 * coordinator} and {@code coordinatorConfigVersion} let the leader reject a request from a stale or
 * non-coordinator node.
 */
public class LeadershipTransferInitiateRequest extends AbstractRaftRequest {

  private final MemberId desiredLeader;
  private final MemberId coordinator;
  private final long coordinatorConfigVersion;

  private LeadershipTransferInitiateRequest(
      final MemberId desiredLeader,
      final MemberId coordinator,
      final long coordinatorConfigVersion) {
    this.desiredLeader = desiredLeader;
    this.coordinator = coordinator;
    this.coordinatorConfigVersion = coordinatorConfigVersion;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The intended successor the coordinator wants leadership to move to. */
  public MemberId desiredLeader() {
    return desiredLeader;
  }

  /** The coordinator that requested the transfer. */
  public MemberId coordinator() {
    return coordinator;
  }

  /** The configuration version the coordinator based its request on. */
  public long coordinatorConfigVersion() {
    return coordinatorConfigVersion;
  }

  @Override
  public MemberId from() {
    return coordinator;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), desiredLeader, coordinator, coordinatorConfigVersion);
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || !getClass().isAssignableFrom(object.getClass())) {
      return false;
    }
    final LeadershipTransferInitiateRequest other = (LeadershipTransferInitiateRequest) object;
    return coordinatorConfigVersion == other.coordinatorConfigVersion
        && desiredLeader.equals(other.desiredLeader)
        && coordinator.equals(other.coordinator);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("desiredLeader", desiredLeader)
        .add("coordinator", coordinator)
        .add("coordinatorConfigVersion", coordinatorConfigVersion)
        .toString();
  }

  /** Leadership-transfer initiate request builder. */
  public static class Builder
      extends AbstractRaftRequest.Builder<Builder, LeadershipTransferInitiateRequest> {

    private MemberId desiredLeader;
    private MemberId coordinator;
    private long coordinatorConfigVersion;

    public Builder withDesiredLeader(final MemberId desiredLeader) {
      this.desiredLeader = checkNotNull(desiredLeader, "desiredLeader cannot be null");
      return this;
    }

    public Builder withCoordinator(final MemberId coordinator) {
      this.coordinator = checkNotNull(coordinator, "coordinator cannot be null");
      return this;
    }

    public Builder withCoordinatorConfigVersion(final long coordinatorConfigVersion) {
      this.coordinatorConfigVersion = coordinatorConfigVersion;
      return this;
    }

    @Override
    protected void validate() {
      super.validate();
      checkNotNull(desiredLeader, "desiredLeader cannot be null");
      checkNotNull(coordinator, "coordinator cannot be null");
    }

    @Override
    public LeadershipTransferInitiateRequest build() {
      validate();
      return new LeadershipTransferInitiateRequest(
          desiredLeader, coordinator, coordinatorConfigVersion);
    }
  }
}
