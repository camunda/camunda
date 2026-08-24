/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import java.util.Objects;

public class BusinessValueConfiguration {

  private Long overviewRefreshInterval;
  private Boolean overviewComputeEnabled;

  public BusinessValueConfiguration() {}

  public Long getOverviewRefreshInterval() {
    return overviewRefreshInterval;
  }

  public void setOverviewRefreshInterval(final Long overviewRefreshInterval) {
    this.overviewRefreshInterval = overviewRefreshInterval;
  }

  /**
   * Whether the business-value overview sweep may run. Turning it off stops the background
   * computation without taking the dashboard down: existing rows stay readable, they simply stop
   * advancing.
   *
   * <p>Absent configuration reads as enabled. A kill switch that failed closed would silently
   * disable the feature on a configuration mistake, which is the opposite of what it is for.
   */
  public boolean isOverviewComputeEnabled() {
    return overviewComputeEnabled == null || overviewComputeEnabled;
  }

  public Boolean getOverviewComputeEnabled() {
    return overviewComputeEnabled;
  }

  public void setOverviewComputeEnabled(final Boolean overviewComputeEnabled) {
    this.overviewComputeEnabled = overviewComputeEnabled;
  }

  @Override
  public int hashCode() {
    return Objects.hash(overviewRefreshInterval, overviewComputeEnabled);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BusinessValueConfiguration that = (BusinessValueConfiguration) o;
    return Objects.equals(overviewRefreshInterval, that.overviewRefreshInterval)
        && Objects.equals(overviewComputeEnabled, that.overviewComputeEnabled);
  }

  @Override
  public String toString() {
    return "BusinessValueConfiguration(overviewRefreshInterval="
        + getOverviewRefreshInterval()
        + ", overviewComputeEnabled="
        + getOverviewComputeEnabled()
        + ")";
  }
}
