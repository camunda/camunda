/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import java.util.Optional;
import java.util.function.Supplier;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Health indicator that signals whether the gateway is aware of partition leaders in the cluster.
 * If the gateway is not aware of any partition leaders, it cannot relay any requests
 */
public class PartitionLeaderAwarenessHealthIndicator implements HealthIndicator {

  private final Supplier<Optional<BrokerClusterState>> clusterStateSupplier;

  public PartitionLeaderAwarenessHealthIndicator(
      final Supplier<Optional<BrokerClusterState>> clusterStateSupplier) {
    this.clusterStateSupplier = requireNonNull(clusterStateSupplier);
  }

  @Override
  public Publisher<HealthResult> getResult() {
    final var optClusterState = clusterStateSupplier.get();

    final HealthStatus healthStatus;
    if (optClusterState.isEmpty()) {
      healthStatus = HealthStatus.DOWN;
    } else {
      final var clusterState = optClusterState.get();
      if (clusterState.getPartitions().stream()
          .anyMatch(
              index ->
                  clusterState.getLeaderForPartition(index) != BrokerClusterState.NODE_ID_NULL)) {
        healthStatus = HealthStatus.UP;
      } else {
        healthStatus = HealthStatus.DOWN;
      }
    }
    return Mono.just(HealthResult.builder(getClass().getSimpleName(), healthStatus).build());
  }
}
