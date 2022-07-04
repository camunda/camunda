/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.liveness;

import io.camunda.zeebe.gateway.impl.probes.health.HealthZeebeClientProperties;
import io.camunda.zeebe.util.health.AbstractDelayedHealthIndicatorProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "management.health.liveness.gateway-responsive")
public class LivenessResponsiveHealthIndicatorProperties
    extends AbstractDelayedHealthIndicatorProperties {

  private HealthZeebeClientProperties healthZeebeClientProperties =
      new HealthZeebeClientProperties();

  public LivenessResponsiveHealthIndicatorProperties() {
    healthZeebeClientProperties.setRequestTimeout(Duration.ofSeconds(5));
  }

  @Override
  protected Duration getDefaultMaxDowntime() {
    return Duration.ofMinutes(10);
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    healthZeebeClientProperties.setRequestTimeout(requestTimeout);
  }

  public HealthZeebeClientProperties getHealthZeebeClientProperties() {
    return healthZeebeClientProperties;
  }

  public void setHealthZeebeClientProperties(
      final HealthZeebeClientProperties healthZeebeClientProperties) {
    this.healthZeebeClientProperties = healthZeebeClientProperties;
  }
}
