/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import io.atomix.primitive.partition.PartitionId;

/**
 * Represents an exceptional error that occurs when a request is trying to be sent to an inactive
 * partition.
 */
public class PartitionInactiveException extends BrokerClientException {
  private static final String DEFAULT_ERROR_MESSAGE =
      "The partition %s is currently INACTIVE with no leader.";
  private final PartitionId partitionId;

  public PartitionInactiveException(final PartitionId partitionId) {
    this(String.format(DEFAULT_ERROR_MESSAGE, partitionId), partitionId);
  }

  public PartitionInactiveException(final String message, final PartitionId partitionId) {
    super(message);
    this.partitionId = partitionId;
  }

  public PartitionId getPartitionId() {
    return partitionId;
  }
}
