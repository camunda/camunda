/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** External configuration properties for {@link ResponsiveHealthIndicator}. */
@ConfigurationProperties(prefix = "management.health.gateway-responsive")
public class ResponsiveHealthIndicatorProperties {

  private HealthZeebeClientProperties healthZeebeClientProperties =
      new HealthZeebeClientProperties();

  public ResponsiveHealthIndicatorProperties() {
    healthZeebeClientProperties.setRequestTimeout(Duration.ofMillis(500));
  }

  public HealthZeebeClientProperties getHealthZeebeClientProperties() {
    return healthZeebeClientProperties;
  }

  public void setHealthZeebeClientProperties(
      final HealthZeebeClientProperties healthZeebeClientProperties) {
    this.healthZeebeClientProperties = healthZeebeClientProperties;
  }

  @Deprecated(forRemoval = true, since = "8.1.0")
  public void setRequestTimeout(final Duration requestTimeout) {
    healthZeebeClientProperties.setRequestTimeout(requestTimeout);
  }
}
