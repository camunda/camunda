/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel;

import io.camunda.optimize.service.mixpanel.client.EventReportingEvent;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSMCondition.class)
public class CCSMEventReportingService implements EventReportingService {

  public CCSMEventReportingService() {}

  @Override
  public void sendEntityEvent(final EventReportingEvent event, final String entityId) {
    // NOOP
  }
}
