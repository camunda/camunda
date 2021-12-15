/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry.mixpanel;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.telemetry.TelemetryReportingService;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelClient;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelEvent;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelEventName;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
@AllArgsConstructor
public class MixpanelReportingService implements TelemetryReportingService {
  private final MixpanelDataService mixpanelDataService;
  private final MixpanelClient mixpanelClient;

  @Override
  public void sendTelemetryData() {
    mixpanelClient.importEvent(
      new MixpanelEvent(MixpanelEventName.HEARTBEAT, mixpanelDataService.getMixpanelHeartbeatProperties())
    );
  }

}
