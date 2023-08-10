/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import io.camunda.zeebe.gateway.impl.MicronautGatewayBridge;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link StartedHealthIndicator}. */
@Singleton
@Requires(property = "management.health.gateway-started.enabled", value = "true")
public class StartedHealthIndicatorAutoConfiguration {

  @Singleton
  @Replaces(named = "gatewayStartedHealthIndicator")
  @Requires("gatewayStartedHealthIndicator")
  public StartedHealthIndicator gatewayStartedHealthIndicator(
      final MicronautGatewayBridge gatewayBridge) {
    // Here we effectively chain two suppliers to decouple their creation in time.
    return new StartedHealthIndicator(gatewayBridge::getGatewayStatus);
  }
}
