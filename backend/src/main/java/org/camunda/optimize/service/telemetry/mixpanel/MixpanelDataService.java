/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry.mixpanel;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelHeartbeatProperties;
import org.camunda.optimize.service.util.configuration.CamundaCloudCondition;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class MixpanelDataService {
  private final ConfigurationService configurationService;
  private final ReportReader reportReader;

  public MixpanelHeartbeatProperties getMixpanelHeartbeatProperties() {
    return new MixpanelHeartbeatProperties(
      reportReader.getReportCount(ReportType.PROCESS),
      reportReader.getReportCount(ReportType.DECISION),
      configurationService.getAnalytics().getMixpanel().getProperties().getOrganizationId()
    );
  }
}
