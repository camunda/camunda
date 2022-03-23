/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry.easytelemetry;


import lombok.AllArgsConstructor;
import org.camunda.optimize.service.telemetry.TelemetryReportingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.TelemetryConfiguration;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CamundaPlatformCondition.class)
@AllArgsConstructor
public class EasyTelemetryReportingService implements TelemetryReportingService {
  private final ConfigurationService configurationService;
  private final EasyTelemetrySendingService telemetrySendingService;
  private final EasyTelemetryDataService telemetryDataService;

  @Override
  public void sendTelemetryData() {
    telemetrySendingService.sendTelemetryData(
      telemetryDataService.getTelemetryData(),
      getTelemetryConfiguration().getTelemetryEndpoint()
    );
  }

  private TelemetryConfiguration getTelemetryConfiguration() {
    return this.configurationService.getTelemetryConfiguration();
  }

}
