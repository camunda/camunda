/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.protocol;

import io.atomix.raft.RaftError;

public final class JoinResponse extends AbstractRaftResponse {

  private JoinResponse(final Status status, final RaftError error) {
    super(status, error);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder extends AbstractRaftResponse.Builder<Builder, JoinResponse> {
    private Builder() {}

    @Override
    public JoinResponse build() {
      validate();
      return new JoinResponse(status, error);
    }
  }
}
