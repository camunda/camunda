/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.BusinessKeyReader;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.reader.VariableUpdateInstanceReader;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.events.CustomTracedCamundaEventFetcherService;
import org.camunda.optimize.service.events.EventFetcherService;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.eventprocess.mediator.EventProcessInstanceImportMediator;
import org.camunda.optimize.service.importing.eventprocess.service.EventProcessInstanceImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class EventProcessInstanceImportMediatorManager implements ConfigurationReloadable {

  private final ConfigurationService configurationService;
  private final OptimizeElasticsearchClient elasticsearchClient;
  private final ObjectMapper objectMapper;
  private final ExternalEventService externalEventService;
  private final CamundaActivityEventReader camundaActivityEventReader;
  private final ProcessDefinitionReader processDefinitionReader;
  private final EventProcessInstanceIndexManager eventBasedProcessIndexManager;
  private final VariableUpdateInstanceReader variableUpdateInstanceReader;
  private final BusinessKeyReader businessKeyReader;

  private final Map<String, List<EventProcessInstanceImportMediator>> importMediators = new ConcurrentHashMap<>();

  public List<EventProcessInstanceImportMediator> getMediatorsByEventProcessPublishId(final String id) {
    return importMediators.get(id);
  }

  public Collection<EventProcessInstanceImportMediator> getActiveMediators() {
    return importMediators.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
  }

  public synchronized void refreshMediators() {
    final Map<String, EventProcessPublishStateDto> availableInstanceIndices =
      eventBasedProcessIndexManager.getPublishedInstanceIndices();

    final List<String> removedPublishedIds = importMediators.keySet().stream()
      .filter(publishedStateId -> !availableInstanceIndices.containsKey(publishedStateId))
      .collect(Collectors.toList());
    removedPublishedIds.forEach(publishedStateId -> {
      final List<EventProcessInstanceImportMediator> eventProcessInstanceImportMediators =
        importMediators.get(publishedStateId);
      eventProcessInstanceImportMediators.forEach(EventProcessInstanceImportMediator::shutdown);
      importMediators.remove(publishedStateId);
    });

    availableInstanceIndices
      .entrySet()
      .stream()
      .filter(entry -> !importMediators.containsKey(entry.getKey()))
      .forEach(entry -> {
        final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
          configurationService
        );
        elasticsearchImportJobExecutor.startExecutingImportJobs();
        final EventProcessInstanceImportService eventProcessInstanceImportService =
          createEventProcessInstanceImportServiceForProcess(entry, elasticsearchImportJobExecutor);
        importMediators.put(
          entry.getKey(),
          createEventProcessInstanceMediators(
            entry.getValue(),
            eventProcessInstanceImportService
          )
        );
      });
  }

  private EventProcessInstanceImportService createEventProcessInstanceImportServiceForProcess(
    final Map.Entry<String, EventProcessPublishStateDto> entry,
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor) {
    return new EventProcessInstanceImportService(
      entry.getValue(),
      elasticsearchImportJobExecutor,
      new EventProcessInstanceWriter(
        new EventProcessInstanceIndex(entry.getKey()), elasticsearchClient, objectMapper
      )
    );
  }

  private List<EventProcessInstanceImportMediator> createEventProcessInstanceMediators(
    final EventProcessPublishStateDto publishedStateDto,
    final EventProcessInstanceImportService eventProcessInstanceImportService) {
    return publishedStateDto.getEventImportSources().stream()
      .map(importSource -> new EventProcessInstanceImportMediator(
        publishedStateDto.getId(),
        importSource,
        configurationService,
        getEventFetcherForEventSource(importSource.getEventSource()),
        eventProcessInstanceImportService
      ))
      .collect(Collectors.toList());
  }

  private EventFetcherService getEventFetcherForEventSource(EventSourceEntryDto eventSourceEntryDto) {
    switch (eventSourceEntryDto.getType()) {
      case EXTERNAL:
        return externalEventService;
      case CAMUNDA:
        return new CustomTracedCamundaEventFetcherService(
          eventSourceEntryDto.getProcessDefinitionKey(),
          eventSourceEntryDto,
          camundaActivityEventReader,
          processDefinitionReader,
          variableUpdateInstanceReader,
          businessKeyReader
        );
      default:
        throw new OptimizeRuntimeException("Cannot find event fetching service for event import source type: "
                                             + eventSourceEntryDto.getType());
    }
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    importMediators.clear();
  }
}
