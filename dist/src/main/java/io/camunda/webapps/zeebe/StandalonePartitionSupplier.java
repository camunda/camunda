/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.util.Either;

public class StandalonePartitionSupplier implements PartitionSupplier {
  private final CamundaClient camundaClient;

  public StandalonePartitionSupplier(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  @Override
  public Either<Exception, Integer> getPartitionsCount() {
    try {
      final var topology = camundaClient.newTopologyRequest().send().join();
      final var partitionCount = topology.getPartitionsCount();
      return Either.right(partitionCount);
    } catch (final Exception t) {
      return Either.left(t);
    }
  }
}
