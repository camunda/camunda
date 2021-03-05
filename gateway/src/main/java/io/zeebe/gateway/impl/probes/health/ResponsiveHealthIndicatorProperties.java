/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.probes.health;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** External configuration properties for {@link ResponsiveHealthIndicator}. */
@ConfigurationProperties(prefix = "management.health.gateway-responsive")
public class ResponsiveHealthIndicatorProperties {

  private Duration requestTimeout = Duration.ofMillis(500);

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    if (requestTimeout.toMillis() <= 0) {
      throw new IllegalArgumentException("requestTimeout must be a positive value");
    }
    this.requestTimeout = requestTimeout;
  }
}
