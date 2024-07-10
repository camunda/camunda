/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

/**
 * Represents an exceptional error that occurs when a request is trying to be sent to an inactive
 * partition.
 */
public class PartitionInactiveException extends BrokerClientException {
  private static final String DEFAULT_ERROR_MESSAGE =
      "The partition %d is currently INACTIVE with no leader.";
  private final int partitionId;

  public PartitionInactiveException(final int partitionId) {
    this(String.format(DEFAULT_ERROR_MESSAGE, partitionId), partitionId);
  }

  public PartitionInactiveException(final String message, final int partitionId) {
    super(message);
    this.partitionId = partitionId;
  }

  public int getPartitionId() {
    return partitionId;
  }
}
