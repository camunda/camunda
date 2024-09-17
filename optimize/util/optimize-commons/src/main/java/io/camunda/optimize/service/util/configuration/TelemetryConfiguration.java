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
import lombok.Data;

@Data
public class TelemetryConfiguration {

  private boolean initializeTelemetry;
  private long reportingIntervalInHours;

  public TelemetryConfiguration(boolean initializeTelemetry, long reportingIntervalInHours) {
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

  public enum Fields {
    initializeTelemetry,
    reportingIntervalInHours
  }
}
