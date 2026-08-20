/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.protocol;

import static java.util.Objects.requireNonNull;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import java.util.Objects;

public final class LeaveRequest extends AbstractRaftRequest {
  private final RaftMember leaving;

  public LeaveRequest(final RaftMember leaving) {
    this.leaving = requireNonNull(leaving);
  }

  public RaftMember leavingMember() {
    return leaving;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public int hashCode() {
    return Objects.hash(leaving);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final LeaveRequest that = (LeaveRequest) o;
    return Objects.equals(leaving, that.leaving);
  }

  @Override
  public MemberId from() {
    // Although it is not strictly required, in the current implementation this request is sent by
    // the member who is leaving.
    return leaving.memberId();
  }

  public static final class Builder
      extends AbstractRaftRequest.Builder<LeaveRequest.Builder, LeaveRequest> {
    private RaftMember leaving;

    private Builder() {}

    public Builder withLeavingMember(final RaftMember leaving) {
      this.leaving = leaving;
      return this;
    }

    @Override
    public LeaveRequest build() {
      validate();
      return new LeaveRequest(leaving);
    }

    @Override
    protected void validate() {
      requireNonNull(leaving, "leaving member cannot be null");
    }
  }
}
