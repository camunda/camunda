/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.probes.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** External configuration properties for {@link MemoryHealthIndicator}. */
@ConfigurationProperties(prefix = "management.health.memory")
public class MemoryHealthIndicatorProperties {

  /** Minimum memory that should be available. */
  private double threshold = 0.1;

  public double getThreshold() {
    return this.threshold;
  }

  public void setThreshold(double threshold) {
    if (threshold <= 0 || threshold >= 1) {
      throw new IllegalArgumentException("Threshold must be a value in the interval ]0,1[");
    }
    this.threshold = threshold;
  }
}
