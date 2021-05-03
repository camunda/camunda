/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl;

import io.zeebe.gateway.Gateway.Status;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Helper class that allows Spring beans to access information from the gateway code that is not
 * managed by Spring
 */
@Component
public class SpringGatewayBridge {

  private Supplier<Status> gatewayStatusSupplier;
  private Supplier<BrokerClusterState> clusterStateSupplier;

  public void registerGatewayStatusSupplier(Supplier<Status> gatewayStatusSupplier) {
    this.gatewayStatusSupplier = gatewayStatusSupplier;
  }

  public void registerClusterStateSupplier(Supplier<BrokerClusterState> clusterStateSupplier) {
    this.clusterStateSupplier = clusterStateSupplier;
  }

  public Optional<Status> getGatewayStatus() {
    if (gatewayStatusSupplier != null) {
      return Optional.of(gatewayStatusSupplier.get());
    } else {
      return Optional.empty();
    }
  }

  public Optional<BrokerClusterState> getClusterState() {
    if (clusterStateSupplier != null) {
      return Optional.ofNullable(clusterStateSupplier.get());
    } else {
      return Optional.empty();
    }
  }
}
