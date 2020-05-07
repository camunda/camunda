/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess.mediator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventProcessEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.BusinessKeyReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.reader.VariableUpdateInstanceReader;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.importing.eventprocess.handler.EventProcessInstanceImportSourceIndexHandler;
import org.camunda.optimize.service.importing.eventprocess.service.CustomTracedEventProcessInstanceImportService;
import org.camunda.optimize.service.importing.eventprocess.service.EventProcessInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventProcessInstanceImportMediatorFactory {
  private final BeanFactory beanFactory;

  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final BackoffCalculator idleBackoffCalculator;

  private final OptimizeElasticsearchClient elasticsearchClient;
  private final EventFetcherFactory eventFetcherFactory;
  private final ProcessDefinitionReader processDefinitionReader;
  private final VariableUpdateInstanceReader variableUpdateInstanceReader;
  private final BusinessKeyReader businessKeyReader;

  public List<EventProcessInstanceImportMediator> createEventProcessInstanceMediators(
    final EventProcessPublishStateDto publishedStateDto) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return publishedStateDto.getEventImportSources().stream()
      .map(importSource -> beanFactory.getBean(
        EventProcessInstanceImportMediator.class,
        publishedStateDto.getId(),
        new EventProcessInstanceImportSourceIndexHandler(configurationService, importSource),
        eventFetcherFactory.createEventFetcherForEventSource(importSource.getEventSource()),
        createImportService(publishedStateDto, importSource.getEventSource()),
        configurationService,
        elasticsearchImportJobExecutor,
        idleBackoffCalculator
      ))
      .collect(Collectors.toList());
  }

  private ImportService<? extends EventProcessEventDto> createImportService(EventProcessPublishStateDto eventProcessPublishStateDto,
                                                                            EventSourceEntryDto eventSourceEntryDto) {
    final EventProcessInstanceImportService eventProcessInstanceImportService = createEventProcessInstanceImportService(
      eventProcessPublishStateDto);
    if (eventSourceEntryDto.getType().equals(EventSourceType.EXTERNAL)) {
      return eventProcessInstanceImportService;
    } else if (eventSourceEntryDto.getType().equals(EventSourceType.CAMUNDA)) {
      return new CustomTracedEventProcessInstanceImportService(
        eventSourceEntryDto,
        new SimpleDateFormat(configurationService.getEngineDateFormat()),
        eventProcessInstanceImportService,
        processDefinitionReader,
        variableUpdateInstanceReader,
        businessKeyReader
      );
    } else {
      throw new RuntimeException(String.format(
        "Cannot create mediator for Event Source Type: %s",
        eventSourceEntryDto.getType()
      ));
    }
  }

  private EventProcessInstanceImportService createEventProcessInstanceImportService(final EventProcessPublishStateDto eventProcessPublishStateDto) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      configurationService
    );
    elasticsearchImportJobExecutor.startExecutingImportJobs();
    return new EventProcessInstanceImportService(
      eventProcessPublishStateDto,
      elasticsearchImportJobExecutor,
      new EventProcessInstanceWriter(
        new EventProcessInstanceIndex(eventProcessPublishStateDto.getId()), elasticsearchClient, objectMapper
      )
    );
  }

}
