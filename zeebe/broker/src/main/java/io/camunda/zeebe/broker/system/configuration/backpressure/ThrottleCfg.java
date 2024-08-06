/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backpressure;

import java.time.Duration;
import java.util.Objects;

public class ThrottleCfg {
  private boolean enabled = false;
  private int acceptableBacklog = 100_000;
  private int minimumLimit = 100;
  private Duration resolution = Duration.ofSeconds(15);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getMinimumLimit() {
    return minimumLimit;
  }

  public void setMinimumLimit(final int minimumLimit) {
    this.minimumLimit = minimumLimit;
  }

  public Duration getResolution() {
    return resolution;
  }

  public void setResolution(final Duration resolution) {
    this.resolution = resolution;
  }

  public int getAcceptableBacklog() {
    return acceptableBacklog;
  }

  public void setAcceptableBacklog(final int acceptableBacklog) {
    this.acceptableBacklog = acceptableBacklog;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, acceptableBacklog, minimumLimit, resolution);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final ThrottleCfg that)) {
      return false;
    }
    return enabled == that.enabled
        && acceptableBacklog == that.acceptableBacklog
        && minimumLimit == that.minimumLimit
        && Objects.equals(resolution, that.resolution);
  }

  @Override
  public String toString() {
    return "ThrottleCfg{"
        + "enabled="
        + enabled
        + ", acceptableBacklog="
        + acceptableBacklog
        + ", minimumLimit="
        + minimumLimit
        + ", resolution="
        + resolution
        + '}';
  }
}
