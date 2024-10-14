/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

public class CloudUserCacheConfiguration {

  private int maxSize;
  private long minFetchIntervalSeconds;

  public CloudUserCacheConfiguration() {}

  public int getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(final int maxSize) {
    this.maxSize = maxSize;
  }

  public long getMinFetchIntervalSeconds() {
    return minFetchIntervalSeconds;
  }

  public void setMinFetchIntervalSeconds(final long minFetchIntervalSeconds) {
    this.minFetchIntervalSeconds = minFetchIntervalSeconds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CloudUserCacheConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + getMaxSize();
    final long $minFetchIntervalSeconds = getMinFetchIntervalSeconds();
    result = result * PRIME + (int) ($minFetchIntervalSeconds >>> 32 ^ $minFetchIntervalSeconds);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CloudUserCacheConfiguration)) {
      return false;
    }
    final CloudUserCacheConfiguration other = (CloudUserCacheConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getMaxSize() != other.getMaxSize()) {
      return false;
    }
    if (getMinFetchIntervalSeconds() != other.getMinFetchIntervalSeconds()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CloudUserCacheConfiguration(maxSize="
        + getMaxSize()
        + ", minFetchIntervalSeconds="
        + getMinFetchIntervalSeconds()
        + ")";
  }
}
