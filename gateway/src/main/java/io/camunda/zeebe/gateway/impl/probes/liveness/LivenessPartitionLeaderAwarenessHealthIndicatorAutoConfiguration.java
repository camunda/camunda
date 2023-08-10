/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.liveness;

import io.camunda.zeebe.gateway.impl.probes.health.PartitionLeaderAwarenessHealthIndicator;
import io.camunda.zeebe.util.health.DelayedHealthIndicator;
import io.camunda.zeebe.util.health.MemoryHealthIndicator;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.management.health.indicator.HealthIndicator;
import jakarta.inject.Singleton;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link MemoryHealthIndicator}. */
@Singleton
@Requires(property = "management.health.gateway-partitionleaderawareness.enabled", value = "true")
public class LivenessPartitionLeaderAwarenessHealthIndicatorAutoConfiguration {

  @Singleton
  @Replaces(named = "livenessGatewayPartitionLeaderAwarenessHealthIndicator")
  @Requires("livenessGatewayPartitionLeaderAwarenessHealthIndicator")
  public HealthIndicator livenessGatewayPartitionLeaderAwarenessHealthIndicator(
      final PartitionLeaderAwarenessHealthIndicator healthIndicator,
      final LivenessPartitionLeaderAwarenessHealthIndicatorProperties properties) {
    return new DelayedHealthIndicator(healthIndicator, properties.getMaxDowntime());
  }
}
