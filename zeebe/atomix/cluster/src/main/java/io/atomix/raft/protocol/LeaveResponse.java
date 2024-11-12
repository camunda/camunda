/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.protocol;

import io.atomix.raft.RaftError;

public final class LeaveResponse extends AbstractRaftResponse {
  private LeaveResponse(final Status status, final RaftError error) {
    super(status, error);
  }

  public static LeaveResponse.Builder builder() {
    return new LeaveResponse.Builder();
  }

  public static final class Builder
      extends AbstractRaftResponse.Builder<LeaveResponse.Builder, LeaveResponse> {
    private Builder() {}

    @Override
    public LeaveResponse build() {
      validate();
      return new LeaveResponse(status, error);
    }
  }
}
