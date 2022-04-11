/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event.mediator;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.EventTraceStateServiceFactory;
import org.camunda.optimize.service.events.CamundaEventService;
import org.camunda.optimize.service.events.CamundaTraceableEventFetcherService;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.importing.event.handler.EventImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.event.service.EventTraceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
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

  public EventTraceImportMediator createCamundaEventTraceImportMediator(final String processDefinitionKey) {
    return beanFactory.getBean(
      EventTraceImportMediator.class,
      beanFactory.getBean(CamundaTraceableEventFetcherService.class, camundaEventService, processDefinitionKey),
      eventImportIndexHandlerRegistry.getCamundaEventTraceImportIndexHandler(processDefinitionKey),
      new EventTraceImportService(
        configurationService,
        eventTraceStateServiceFactory.createEventTraceStateService(processDefinitionKey)
      ),
      configurationService,
      idleBackoffCalculator
    );
  }

  public EventTraceImportMediator createExternalEventTraceImportMediator() {
    return beanFactory.getBean(
      EventTraceImportMediator.class,
      externalEventService,
      eventImportIndexHandlerRegistry.getExternalEventTraceImportIndexHandler(),
      new EventTraceImportService(
        configurationService,
        eventTraceStateServiceFactory.createEventTraceStateService(ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX)
      ),
      configurationService,
      idleBackoffCalculator
    );
  }

}
