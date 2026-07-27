/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.gateway.health.Status;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import java.util.Map;
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
  private Supplier<Map<String, BrokerClusterState>> clusterStatesSupplier;
  private Supplier<JobStreamClient> jobStreamClientSupplier;

  public void registerGatewayStatusSupplier(final Supplier<Status> gatewayStatusSupplier) {
    this.gatewayStatusSupplier = gatewayStatusSupplier;
  }

  /**
   * Registers a supplier of the cluster topology for every known physical tenant (partition group),
   * keyed by physical tenant id. Used by health indicators that must aggregate across all physical
   * tenants rather than the default one only.
   */
  public void registerClusterStatesSupplier(
      final Supplier<Map<String, BrokerClusterState>> clusterStatesSupplier) {
    this.clusterStatesSupplier = clusterStatesSupplier;
  }

  public Optional<Status> getGatewayStatus() {
    return Optional.ofNullable(gatewayStatusSupplier).map(Supplier::get);
  }

  /**
   * Returns the cluster topology for every known physical tenant, keyed by physical tenant id.
   * Returns an empty map when no supplier has been registered yet or the registered supplier yields
   * {@code null}.
   */
  public Map<String, BrokerClusterState> getClusterStates() {
    return Optional.ofNullable(clusterStatesSupplier).map(Supplier::get).orElse(Map.of());
  }

  public Optional<JobStreamClient> getJobStreamClient() {
    return Optional.ofNullable(jobStreamClientSupplier).map(Supplier::get);
  }

  public void registerJobStreamClient(final Supplier<JobStreamClient> jobStreamClientSupplier) {
    this.jobStreamClientSupplier = jobStreamClientSupplier;
  }
}
