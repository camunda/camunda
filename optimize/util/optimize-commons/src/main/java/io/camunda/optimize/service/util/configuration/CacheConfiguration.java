/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import java.util.Objects;

public class CacheConfiguration {

  private int maxSize;
  private int defaultTtlMillis;

  public CacheConfiguration() {}

  public int getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(final int maxSize) {
    this.maxSize = maxSize;
  }

  public int getDefaultTtlMillis() {
    return defaultTtlMillis;
  }

  public void setDefaultTtlMillis(final int defaultTtlMillis) {
    this.defaultTtlMillis = defaultTtlMillis;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CacheConfiguration;
  }

  @Override
  public int hashCode() {
    return Objects.hash(defaultTtlMillis, maxSize);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CacheConfiguration that = (CacheConfiguration) o;
    return Objects.equals(defaultTtlMillis, that.defaultTtlMillis)
        && Objects.equals(maxSize, that.maxSize);
  }

  @Override
  public String toString() {
    return "CacheConfiguration(maxSize="
        + getMaxSize()
        + ", defaultTtlMillis="
        + getDefaultTtlMillis()
        + ")";
  }
}
