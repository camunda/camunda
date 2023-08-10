/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.liveness;

import io.camunda.zeebe.gateway.impl.probes.health.ClusterAwarenessHealthIndicator;
import io.camunda.zeebe.util.health.DelayedHealthIndicator;
import io.camunda.zeebe.util.health.MemoryHealthIndicator;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.management.health.indicator.HealthIndicator;
import jakarta.inject.Singleton;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link MemoryHealthIndicator}. */
@Singleton
@Requires(property = "management.health.gateway-clusterawareness.enabled", value = "true")
public class LivenessClusterAwarenessHealthIndicatorAutoConfiguration {

  @Singleton
  @Replaces(named = "livenessGatewayClusterAwarenessHealthIndicator")
  @Requires("livenessGatewayClusterAwarenessHealthIndicator")
  public HealthIndicator livenessGatewayClusterAwarenessHealthIndicator(
      final ClusterAwarenessHealthIndicator healthIndicator,
      final LivenessClusterAwarenessHealthIndicatorProperties properties) {
    return new DelayedHealthIndicator(healthIndicator, properties.getMaxDowntime());
  }
}
