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

public final class JoinRequest extends AbstractRaftRequest {
  private final RaftMember joining;

  private JoinRequest(final RaftMember joining) {
    this.joining = requireNonNull(joining);
  }

  public RaftMember joiningMember() {
    return joining;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public int hashCode() {
    return Objects.hash(joining);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JoinRequest that = (JoinRequest) o;
    return Objects.equals(joining, that.joining);
  }

  @Override
  public String toString() {
    return "JoinRequest{" + "joining=" + joining + '}';
  }

  @Override
  public MemberId from() {
    // Although it is not strictly required, in the current implementation this request is sent by
    // the member who is joining.
    return joining.memberId();
  }

  public static final class Builder
      extends AbstractRaftRequest.Builder<JoinRequest.Builder, JoinRequest> {

    private RaftMember joining;

    private Builder() {}

    public Builder withJoiningMember(final RaftMember joining) {
      this.joining = joining;
      return this;
    }

    @Override
    public JoinRequest build() {
      validate();
      return new JoinRequest(joining);
    }

    @Override
    protected void validate() {
      requireNonNull(joining, "joining member cannot be null");
    }
  }
}
