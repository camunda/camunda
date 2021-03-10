/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.probes.liveness;

import io.zeebe.util.health.AbstractDelayedHealthIndicatorProperties;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "management.health.liveness.gateway-responsive")
public class LivenessResponsiveHealthIndicatorProperties
    extends AbstractDelayedHealthIndicatorProperties {

  private Duration requestTimeout = Duration.ofSeconds(5);

  @Override
  protected Duration getDefaultMaxDowntime() {
    return Duration.ofMinutes(10);
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    if (Objects.requireNonNull(requestTimeout).toMillis() <= 0) {
      throw new IllegalArgumentException("requestTimeout must be greater than 0");
    }
    this.requestTimeout = requestTimeout;
  }
}
