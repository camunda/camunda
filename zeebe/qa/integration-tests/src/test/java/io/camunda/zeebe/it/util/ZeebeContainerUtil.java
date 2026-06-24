/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.util;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.container.cluster.CamundaCluster;
import io.camunda.container.cluster.GatewayNode;

public final class ZeebeContainerUtil {

  private ZeebeContainerUtil() {
    // utility class
  }

  public static CamundaClientBuilder newClientBuilder(final CamundaCluster cluster) {
    final GatewayNode<?> gateway = cluster.getAvailableGateway();

    return CamundaClient.newClientBuilder()
        .preferRestOverGrpc(false)
        .grpcAddress(gateway.getGrpcAddress());
  }

  public static CamundaClientBuilder newClientBuilder(final GatewayNode gateway) {
    return CamundaClient.newClientBuilder()
        .preferRestOverGrpc(false)
        .grpcAddress(gateway.getGrpcAddress());
  }
}
