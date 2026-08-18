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

  public BusinessValueConfiguration() {}

  public Long getOverviewRefreshInterval() {
    return overviewRefreshInterval;
  }

  public void setOverviewRefreshInterval(final Long overviewRefreshInterval) {
    this.overviewRefreshInterval = overviewRefreshInterval;
  }

  @Override
  public int hashCode() {
    return Objects.hash(overviewRefreshInterval);
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
    return Objects.equals(overviewRefreshInterval, that.overviewRefreshInterval);
  }

  @Override
  public String toString() {
    return "BusinessValueConfiguration(overviewRefreshInterval="
        + getOverviewRefreshInterval()
        + ")";
  }
}
