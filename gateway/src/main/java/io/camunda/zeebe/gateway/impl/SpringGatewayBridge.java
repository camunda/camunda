/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl;

import io.camunda.zeebe.gateway.health.Status;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
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
  private Supplier<Optional<BrokerClusterState>> clusterStateSupplier;
  private Supplier<BrokerClient> brokerClientSupplier;

  public void registerGatewayStatusSupplier(final Supplier<Status> gatewayStatusSupplier) {
    this.gatewayStatusSupplier = gatewayStatusSupplier;
  }

  public void registerClusterStateSupplier(
      final Supplier<Optional<BrokerClusterState>> clusterStateSupplier) {
    this.clusterStateSupplier = clusterStateSupplier;
  }

  public void registerBrokerClientSupplier(final Supplier<BrokerClient> brokerClientSupplier) {
    this.brokerClientSupplier = brokerClientSupplier;
  }

  public Optional<Status> getGatewayStatus() {
    return Optional.ofNullable(gatewayStatusSupplier).map(Supplier::get);
  }

  public Optional<BrokerClusterState> getClusterState() {
    return Optional.ofNullable(clusterStateSupplier).flatMap(Supplier::get);
  }

  public Optional<BrokerClient> getBrokerClient() {
    return Optional.ofNullable(brokerClientSupplier).map(Supplier::get);
  }
}
