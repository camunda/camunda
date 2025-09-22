/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

public class ProcessCache {
  /** Process cache max size */
  private int maxSize = 100;

  /** Process cache expiration */
  private Duration expirationIdle = Duration.ofMillis(0);

  public int getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(final int maxSize) {
    this.maxSize = maxSize;
  }

  public Duration getExpirationIdle() {
    return expirationIdle;
  }

  public void setExpirationIdle(final Duration expirationIdle) {
    this.expirationIdle = expirationIdle;
  }
}
