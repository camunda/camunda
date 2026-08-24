/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import java.util.Objects;

public class DeletedProcessDefinitionCacheConfiguration {

  private int maxSize;
  private long refreshIntervalSeconds;

  public DeletedProcessDefinitionCacheConfiguration() {}

  public int getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(final int maxSize) {
    this.maxSize = maxSize;
  }

  public long getRefreshIntervalSeconds() {
    return refreshIntervalSeconds;
  }

  public void setRefreshIntervalSeconds(final long refreshIntervalSeconds) {
    this.refreshIntervalSeconds = refreshIntervalSeconds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DeletedProcessDefinitionCacheConfiguration;
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxSize, refreshIntervalSeconds);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeletedProcessDefinitionCacheConfiguration that =
        (DeletedProcessDefinitionCacheConfiguration) o;
    return Objects.equals(maxSize, that.maxSize)
        && Objects.equals(refreshIntervalSeconds, that.refreshIntervalSeconds);
  }

  @Override
  public String toString() {
    return "DeletedProcessDefinitionCacheConfiguration(maxSize="
        + getMaxSize()
        + ", refreshIntervalSeconds="
        + getRefreshIntervalSeconds()
        + ")";
  }
}
