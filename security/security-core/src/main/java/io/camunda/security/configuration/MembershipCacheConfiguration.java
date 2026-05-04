/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.time.Duration;

public class MembershipCacheConfiguration {

  public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
  public static final int DEFAULT_MAX_SIZE = 10_000;

  private boolean enabled = true;
  private Duration ttl = DEFAULT_TTL;
  private int maxSize = DEFAULT_MAX_SIZE;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getTtl() {
    return ttl;
  }

  public void setTtl(final Duration ttl) {
    this.ttl = ttl;
  }

  public int getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(final int maxSize) {
    this.maxSize = maxSize;
  }
}
