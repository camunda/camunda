/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.mixpanel;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.mixpanel.client.EventReportingEvent;
import org.camunda.optimize.service.mixpanel.client.MixpanelClient;
import org.camunda.optimize.service.mixpanel.client.MixpanelEvent;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
@AllArgsConstructor
public class MixpanelReportingService {
  private final MixpanelDataService mixpanelDataService;
  private final MixpanelClient mixpanelClient;

  public void sendHeartbeatData() {
    mixpanelClient.importEvent(
        new MixpanelEvent(
            EventReportingEvent.HEARTBEAT, mixpanelDataService.getMixpanelHeartbeatProperties()));
  }

  public void sendEntityEvent(final EventReportingEvent event, final String entityId) {
    mixpanelClient.importEvent(
        new MixpanelEvent(event, mixpanelDataService.getMixpanelEntityEventProperties(entityId)));
  }
}
