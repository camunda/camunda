/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.cmd;

/**
 * Represents an exceptional error that occurs when a partition can not be found. For example this
 * can happen when the element instance key does not refer to a known partition.
 */
public class PartitionNotFoundException extends ClientException {
  private static final String DEFAULT_ERROR_MESSAGE =
      "Expected to execute command on partition %d, but either it does not exist, or the gateway is not yet aware of it";
  private final int partitionId;

  public PartitionNotFoundException(final int partitionId) {
    this(String.format(DEFAULT_ERROR_MESSAGE, partitionId), partitionId);
  }

  public PartitionNotFoundException(final String message, final int partitionId) {
    super(message);
    this.partitionId = partitionId;
  }

  public PartitionNotFoundException(
      final String message, final Throwable cause, final int partitionId) {
    super(message, cause);
    this.partitionId = partitionId;
  }

  public int getPartitionId() {
    return partitionId;
  }
}
