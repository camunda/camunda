/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.TELEMETRY_CONFIGURATION;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;

public class TelemetryConfiguration {

  private boolean initializeTelemetry;
  private long reportingIntervalInHours;

  public TelemetryConfiguration(
      final boolean initializeTelemetry, final long reportingIntervalInHours) {
    this.initializeTelemetry = initializeTelemetry;
    this.reportingIntervalInHours = reportingIntervalInHours;
  }

  protected TelemetryConfiguration() {}

  public void validate() {
    if (reportingIntervalInHours <= 0) {
      throw new OptimizeConfigurationException(
          String.format(
              "%s.%s must be set to a positive number",
              TELEMETRY_CONFIGURATION, Fields.reportingIntervalInHours.name()));
    }
  }

  public boolean isInitializeTelemetry() {
    return initializeTelemetry;
  }

  public void setInitializeTelemetry(final boolean initializeTelemetry) {
    this.initializeTelemetry = initializeTelemetry;
  }

  public long getReportingIntervalInHours() {
    return reportingIntervalInHours;
  }

  public void setReportingIntervalInHours(final long reportingIntervalInHours) {
    this.reportingIntervalInHours = reportingIntervalInHours;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof TelemetryConfiguration;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "TelemetryConfiguration(initializeTelemetry="
        + isInitializeTelemetry()
        + ", reportingIntervalInHours="
        + getReportingIntervalInHours()
        + ")";
  }

  public enum Fields {
    initializeTelemetry,
    reportingIntervalInHours
  }
}
