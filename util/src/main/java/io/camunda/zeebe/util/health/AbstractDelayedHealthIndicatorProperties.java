/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.health;

import static java.util.Objects.requireNonNull;

import java.time.Duration;

public abstract class AbstractDelayedHealthIndicatorProperties {

  private Duration maxDowntime = getDefaultMaxDowntime();

  protected abstract Duration getDefaultMaxDowntime();

  public Duration getMaxDowntime() {
    return maxDowntime;
  }

  public void setMaxDowntime(final Duration maxDowntime) {
    if (requireNonNull(maxDowntime).toMillis() < 0) {
      throw new IllegalArgumentException("MaxDowntime must be >= 0");
    }

    this.maxDowntime = maxDowntime;
  }
}
