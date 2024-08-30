/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
