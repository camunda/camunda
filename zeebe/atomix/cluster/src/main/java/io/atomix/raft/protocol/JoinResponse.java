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

  // The index of the configuration entry that admits the joining member, or 0 on error. The
  // joiner waits for this entry to be durably present in its own log before considering the join
  // complete - see ReconfigurationHelper#join - since the leader may commit it without the
  // joiner's participation, e.g. a PROMOTABLE joiner is in no quorum.
  private final long index;

  private JoinResponse(final Status status, final RaftError error, final long index) {
    super(status, error);
    this.index = index;
  }

  public static Builder builder() {
    return new Builder();
  }

  public long index() {
    return index;
  }

  public static final class Builder extends AbstractRaftResponse.Builder<Builder, JoinResponse> {
    private long index;

    private Builder() {}

    public Builder withIndex(final long index) {
      this.index = index;
      return this;
    }

    @Override
    public JoinResponse build() {
      validate();
      return new JoinResponse(status, error, index);
    }
  }
}
