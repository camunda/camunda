/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.mixpanel;

import io.camunda.optimize.service.mixpanel.client.EventReportingEvent;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
@AllArgsConstructor
public class CCSaaSEventReportingService implements EventReportingService {

  private final MixpanelReportingService mixpanelReportingService;

  @Override
  public void sendEntityEvent(final EventReportingEvent event, final String entityId) {
    mixpanelReportingService.sendEntityEvent(event, entityId);
  }
}
