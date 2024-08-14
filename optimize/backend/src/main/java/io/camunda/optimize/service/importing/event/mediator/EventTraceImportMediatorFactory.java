/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.event.mediator;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.events.EventTraceStateServiceFactory;
import io.camunda.optimize.service.events.ExternalEventService;
import io.camunda.optimize.service.importing.event.handler.EventImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.event.service.EventTraceImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class EventTraceImportMediatorFactory {

  private final ConfigurationService configurationService;
  private final EventImportIndexHandlerRegistry eventImportIndexHandlerRegistry;
  private final EventTraceStateServiceFactory eventTraceStateServiceFactory;
  private final BeanFactory beanFactory;
  private final ExternalEventService externalEventService;
  private final BackoffCalculator idleBackoffCalculator;
  private final DatabaseClient databaseClient;

  public EventTraceImportMediator createExternalEventTraceImportMediator() {
    return beanFactory.getBean(
        EventTraceImportMediator.class,
        externalEventService,
        eventImportIndexHandlerRegistry.getExternalEventTraceImportIndexHandler(),
        new EventTraceImportService(
            configurationService,
            eventTraceStateServiceFactory.createEventTraceStateService(
                DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX),
            databaseClient),
        configurationService,
        idleBackoffCalculator);
  }
}
