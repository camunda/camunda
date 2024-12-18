/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.util;

import io.camunda.client.ZeebeClient;
import io.camunda.client.ZeebeClientBuilder;
import io.zeebe.containers.ZeebeGatewayNode;
import io.zeebe.containers.cluster.ZeebeCluster;
import io.zeebe.containers.engine.ContainerEngine;

public final class ZeebeContainerUtil {

  private ZeebeContainerUtil() {
    // utility class
  }

  public static ZeebeClientBuilder newClientBuilder(final ZeebeCluster cluster) {
    final ZeebeGatewayNode<?> gateway = cluster.getAvailableGateway();

    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .gatewayAddress(gateway.getExternalGatewayAddress());
  }

  public static ZeebeClientBuilder newClientBuilder(final ContainerEngine containerEngine) {
    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .gatewayAddress(containerEngine.getGatewayAddress());
  }
}
