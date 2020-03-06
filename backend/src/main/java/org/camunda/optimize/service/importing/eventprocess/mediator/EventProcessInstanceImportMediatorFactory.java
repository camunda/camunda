/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess.mediator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.importing.eventprocess.handler.EventProcessInstanceImportSourceIndexHandler;
import org.camunda.optimize.service.importing.eventprocess.service.EventProcessInstanceImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventProcessInstanceImportMediatorFactory {
  private final BeanFactory beanFactory;

  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  private final OptimizeElasticsearchClient elasticsearchClient;
  private final EventFetcherFactory eventFetcherFactory;

  public List<EventProcessInstanceImportMediator> createEventProcessInstanceMediators(
    final EventProcessPublishStateDto publishedStateDto) {
    return publishedStateDto.getEventImportSources().stream()
      .map(importSource -> beanFactory.getBean(
        EventProcessInstanceImportMediator.class,
        publishedStateDto.getId(),
        new EventProcessInstanceImportSourceIndexHandler(configurationService, importSource),
        eventFetcherFactory.createEventFetcherForEventSource(importSource.getEventSource()),
        createEventProcessInstanceImportServiceForProcess(publishedStateDto)
      ))
      .collect(Collectors.toList());
  }

  private EventProcessInstanceImportService createEventProcessInstanceImportServiceForProcess(
    final EventProcessPublishStateDto publishStateDto) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      configurationService
    );
    elasticsearchImportJobExecutor.startExecutingImportJobs();
    return new EventProcessInstanceImportService(
      publishStateDto,
      elasticsearchImportJobExecutor,
      new EventProcessInstanceWriter(
        new EventProcessInstanceIndex(publishStateDto.getId()), elasticsearchClient, objectMapper
      )
    );
  }

}
