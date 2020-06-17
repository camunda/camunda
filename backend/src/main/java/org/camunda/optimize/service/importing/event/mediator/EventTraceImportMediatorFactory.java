/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event.mediator;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.EventTraceStateServiceFactory;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
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
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return beanFactory.getBean(
      EventTraceImportMediator.class,
      beanFactory.getBean(CamundaTraceableEventFetcherService.class, camundaEventService, processDefinitionKey),
      eventImportIndexHandlerRegistry.getCamundaEventTraceImportIndexHandler(processDefinitionKey),
      new EventTraceImportService(
        elasticsearchImportJobExecutor,
        eventTraceStateServiceFactory.createEventTraceStateService(processDefinitionKey)
      ),
      configurationService,
      idleBackoffCalculator
    );
  }

  public EventTraceImportMediator createExternalEventTraceImportMediator() {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return beanFactory.getBean(
      EventTraceImportMediator.class,
      externalEventService,
      eventImportIndexHandlerRegistry.getExternalEventTraceImportIndexHandler(),
      new EventTraceImportService(
        elasticsearchImportJobExecutor,
        eventTraceStateServiceFactory.createEventTraceStateService(ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX)
      ),
      configurationService,
      idleBackoffCalculator
    );
  }

}
