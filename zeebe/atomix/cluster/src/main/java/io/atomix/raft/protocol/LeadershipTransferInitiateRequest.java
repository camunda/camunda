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
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RebalanceConfiguration;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Initiate request for a coordinated leadership transfer.
 *
 * <p>Sent by the rebalancing coordinator to a partition's current leader to ask it to hand
 * leadership to {@code desiredLeader}. The leader validates the request and either rejects it
 * immediately (returning a skip result in the {@link LeadershipTransferInitiateResponse}) or
 * accepts it and drives the transfer, reporting the terminal outcome asynchronously via a {@link
 * LeadershipTransferResultRequest} carrying the same {@code correlationId}. The {@code coordinator}
 * and {@code coordinatorConfigVersion} let the leader reject a request from a stale or
 * non-coordinator node.
 *
 * <p>The two halves travel on separate subjects rather than as one request/response pair because a
 * transfer takes far longer than the {@code requestTimeout} bounding a single round trip, and has
 * to survive the leader it was sent to stepping down. The {@code correlationId} is what ties them
 * back together.
 *
 * <p>The request may also override the leader's default rebalance settings for transfer. Whatever
 * it leaves unset keeps the default value; see {@link #effectiveConfiguration}.
 */
public final class LeadershipTransferInitiateRequest extends AbstractRaftRequest {

  private final MemberId desiredLeader;
  private final MemberId coordinator;
  private final long coordinatorConfigVersion;
  private final long correlationId;
  private final @Nullable Long replicationLagThreshold;
  private final @Nullable Duration replicationTimeout;
  private final @Nullable Integer maxTransferAttempts;

  private LeadershipTransferInitiateRequest(
      final MemberId desiredLeader,
      final MemberId coordinator,
      final long coordinatorConfigVersion,
      final long correlationId,
      final @Nullable Long replicationLagThreshold,
      final @Nullable Duration replicationTimeout,
      final @Nullable Integer maxTransferAttempts) {
    this.desiredLeader = desiredLeader;
    this.coordinator = coordinator;
    this.coordinatorConfigVersion = coordinatorConfigVersion;
    this.correlationId = correlationId;
    this.replicationLagThreshold = replicationLagThreshold;
    this.replicationTimeout = replicationTimeout;
    this.maxTransferAttempts = maxTransferAttempts;
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

  /** The version of the committed cluster configuration the coordinator based its request on. */
  public long coordinatorConfigVersion() {
    return coordinatorConfigVersion;
  }

  /**
   * The coordinator-generated id of the rebalance operation this transfer belongs to. Echoed back
   * in the {@link LeadershipTransferResultRequest} so the coordinator can tell the result of this
   * operation apart from a delayed result of an earlier one.
   */
  public long correlationId() {
    return correlationId;
  }

  /**
   * The maximum replication lag, in bytes, the desired leader may have for this transfer to be
   * accepted (or {@code null} to use the configured default value).
   */
  public @Nullable Long replicationLagThreshold() {
    return replicationLagThreshold;
  }

  /**
   * How long the partition may stay frozen waiting for the desired leader to catch up (or {@code
   * null} to use the configured default value).
   */
  public @Nullable Duration replicationTimeout() {
    return replicationTimeout;
  }

  /**
   * How many TimeoutNow requests the leader may send before giving up (or {@code null} to use the
   * configured default value).
   */
  public @Nullable Integer maxTransferAttempts() {
    return maxTransferAttempts;
  }

  /** The settings this transfer runs under. */
  public RebalanceConfiguration effectiveConfiguration(final RebalanceConfiguration configured) {
    return new RebalanceConfiguration(
        replicationLagThreshold != null
            ? replicationLagThreshold
            : configured.replicationLagThreshold(),
        replicationTimeout != null ? replicationTimeout : configured.replicationTimeout(),
        maxTransferAttempts != null ? maxTransferAttempts : configured.maxTransferAttempts());
  }

  @Override
  public MemberId from() {
    return coordinator;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getClass(),
        desiredLeader,
        coordinator,
        coordinatorConfigVersion,
        correlationId,
        replicationLagThreshold,
        replicationTimeout,
        maxTransferAttempts);
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
        && correlationId == other.correlationId
        && desiredLeader.equals(other.desiredLeader)
        && coordinator.equals(other.coordinator)
        && Objects.equals(replicationLagThreshold, other.replicationLagThreshold)
        && Objects.equals(replicationTimeout, other.replicationTimeout)
        && Objects.equals(maxTransferAttempts, other.maxTransferAttempts);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("desiredLeader", desiredLeader)
        .add("coordinator", coordinator)
        .add("coordinatorConfigVersion", coordinatorConfigVersion)
        .add("correlationId", correlationId)
        .add("replicationLagThreshold", replicationLagThreshold)
        .add("replicationTimeout", replicationTimeout)
        .add("maxTransferAttempts", maxTransferAttempts)
        .toString();
  }

  /** Leadership-transfer initiate request builder. */
  public static class Builder
      extends AbstractRaftRequest.Builder<Builder, LeadershipTransferInitiateRequest> {

    private MemberId desiredLeader;
    private MemberId coordinator;
    private long coordinatorConfigVersion;
    private long correlationId;
    private @Nullable Long replicationLagThreshold;
    private @Nullable Duration replicationTimeout;
    private @Nullable Integer maxTransferAttempts;

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

    public Builder withCorrelationId(final long correlationId) {
      this.correlationId = correlationId;
      return this;
    }

    public Builder withReplicationLagThreshold(final long replicationLagThreshold) {
      checkArgument(
          replicationLagThreshold >= 0,
          "replicationLagThreshold must not be negative but was %s",
          replicationLagThreshold);
      this.replicationLagThreshold = replicationLagThreshold;
      return this;
    }

    public Builder withReplicationTimeout(final Duration replicationTimeout) {
      checkNotNull(replicationTimeout, "replicationTimeout cannot be null");
      checkArgument(
          !replicationTimeout.isZero() && !replicationTimeout.isNegative(),
          "replicationTimeout must be positive but was %s",
          replicationTimeout);
      this.replicationTimeout = replicationTimeout;
      return this;
    }

    public Builder withMaxTransferAttempts(final int maxTransferAttempts) {
      checkArgument(
          maxTransferAttempts > 0,
          "maxTransferAttempts must be positive but was %s",
          maxTransferAttempts);
      this.maxTransferAttempts = maxTransferAttempts;
      return this;
    }

    @Override
    protected void validate() {
      super.validate();
      checkNotNull(desiredLeader, "desiredLeader cannot be null");
      checkNotNull(coordinator, "coordinator cannot be null");
      checkArgument(correlationId != 0, "correlationId must be set");
    }

    @Override
    public LeadershipTransferInitiateRequest build() {
      validate();
      return new LeadershipTransferInitiateRequest(
          desiredLeader,
          coordinator,
          coordinatorConfigVersion,
          correlationId,
          replicationLagThreshold,
          replicationTimeout,
          maxTransferAttempts);
    }
  }
}
