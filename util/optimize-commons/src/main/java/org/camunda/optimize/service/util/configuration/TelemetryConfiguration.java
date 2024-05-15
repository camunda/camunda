/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.TELEMETRY_CONFIGURATION;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@FieldNameConstants(asEnum = true)
@Data
public class TelemetryConfiguration {
  private boolean initializeTelemetry;
  private long reportingIntervalInHours;

  public void validate() {
    if (reportingIntervalInHours <= 0) {
      throw new OptimizeConfigurationException(
          String.format(
              "%s.%s must be set to a positive number",
              TELEMETRY_CONFIGURATION, Fields.reportingIntervalInHours.name()));
    }
  }
}
