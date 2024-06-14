/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.event.mediator;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.events.EventTraceStateServiceFactory;
import io.camunda.optimize.service.events.CamundaEventService;
import io.camunda.optimize.service.events.CamundaTraceableEventFetcherService;
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
  private final CamundaEventService camundaEventService;
  private final ExternalEventService externalEventService;
  private final BackoffCalculator idleBackoffCalculator;
  private final DatabaseClient databaseClient;

  public EventTraceImportMediator createCamundaEventTraceImportMediator(
      final String processDefinitionKey) {
    return beanFactory.getBean(
        EventTraceImportMediator.class,
        beanFactory.getBean(
            CamundaTraceableEventFetcherService.class, camundaEventService, processDefinitionKey),
        eventImportIndexHandlerRegistry.getCamundaEventTraceImportIndexHandler(
            processDefinitionKey),
        new EventTraceImportService(
            configurationService,
            eventTraceStateServiceFactory.createEventTraceStateService(processDefinitionKey),
            databaseClient),
        configurationService,
        idleBackoffCalculator);
  }

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
