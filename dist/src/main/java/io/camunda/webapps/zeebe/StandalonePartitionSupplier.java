/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.zeebe;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.util.Either;

public class StandalonePartitionSupplier implements PartitionSupplier {
  private final ZeebeClient zeebeClient;

  public StandalonePartitionSupplier(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  @Override
  public Either<Exception, Integer> getPartitionsCount() {
    try {
      final var topology = zeebeClient.newTopologyRequest().send().join();
      final var partitionCount = topology.getPartitionsCount();
      return Either.right(partitionCount);
    } catch (final Exception t) {
      return Either.left(t);
    }
  }
}
