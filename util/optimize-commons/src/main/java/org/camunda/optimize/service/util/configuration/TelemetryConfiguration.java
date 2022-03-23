/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.TELEMETRY_CONFIGURATION;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@FieldNameConstants(asEnum = true)
@Data
public class TelemetryConfiguration {
  private boolean initializeTelemetry;
  private long reportingIntervalInHours;
  private String telemetryEndpoint;

  public void validate() {
    if (reportingIntervalInHours <= 0) {
      throw new OptimizeConfigurationException(
        String.format(
          "%s.%s must be set to a positive number",
          TELEMETRY_CONFIGURATION,
          Fields.reportingIntervalInHours.name()
        )
      );
    }
    if (StringUtils.isEmpty(telemetryEndpoint)) {
      throw new OptimizeConfigurationException(
        String.format(
          "%s.%s must be set and must not be empty",
          TELEMETRY_CONFIGURATION,
          Fields.telemetryEndpoint.name()
        ));
    }
  }
}
