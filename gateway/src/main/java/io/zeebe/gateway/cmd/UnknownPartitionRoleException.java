/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.cmd;

import io.zeebe.protocol.PartitionState;

public final class UnknownPartitionRoleException extends ClientException {
  private static final String FORMAT =
      "Expected broker role for partition '%d' to be one of [LEADER, FOLLOWER], but got '%s'";

  private final int partitionId;
  private final PartitionState state;

  public UnknownPartitionRoleException(final int partitionId, final PartitionState state) {
    this(partitionId, state, null);
  }

  public UnknownPartitionRoleException(
      final int partitionId, final PartitionState state, final Throwable cause) {
    super(String.format(FORMAT, partitionId, state), cause);
    this.partitionId = partitionId;
    this.state = state;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public PartitionState getState() {
    return state;
  }
}
