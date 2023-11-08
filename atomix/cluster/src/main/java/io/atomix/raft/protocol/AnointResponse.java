/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.protocol;

import io.atomix.raft.RaftError;
import io.atomix.raft.protocol.RaftResponse.Builder;

public class AnointResponse extends AbstractRaftResponse {

  protected AnointResponse(final Status status, final RaftError error) {
    super(status, error);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder extends AbstractRaftResponse.Builder<Builder, AnointResponse> {

    @Override
    public AnointResponse build() {
      validate();
      return new AnointResponse(status, error);
    }
  }
}
