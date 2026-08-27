/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import org.jspecify.annotations.NullMarked;

/**
 * Represents an exceptional error that occurs when a request is sent to a partition that is in
 * recovery mode. While a partition is recovering, only backup and recovery related requests are
 * served; every other request is rejected with this exception.
 */
@NullMarked
public class PartitionInRecoveryException extends BrokerClientException {
  private static final String DEFAULT_ERROR_MESSAGE = "Partition %d is currently in recovery mode.";
  private final int partitionId;

  public PartitionInRecoveryException(final int partitionId) {
    super(String.format(DEFAULT_ERROR_MESSAGE, partitionId));
    this.partitionId = partitionId;
  }

  public int getPartitionId() {
    return partitionId;
  }
}
