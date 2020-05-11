/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.backpressure;

public final class ResourceExhaustedException extends RuntimeException {
  private static final String MESSAGE_FORMAT =
      "Congestion detected for partition %d, load will be limited until there is no more congestion";
  private static final long serialVersionUID = -769858442656106208L;

  private final int partitionId;

  public ResourceExhaustedException(final int partitionId) {
    super(String.format(MESSAGE_FORMAT, partitionId));

    this.partitionId = partitionId;
  }

  public int getPartitionId() {
    return partitionId;
  }
}
