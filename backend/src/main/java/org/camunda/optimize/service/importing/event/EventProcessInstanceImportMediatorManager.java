/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.EventReader;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class EventProcessInstanceImportMediatorManager implements ConfigurationReloadable {
  private ConfigurationService configurationService;
  private OptimizeElasticsearchClient elasticsearchClient;
  private ObjectMapper objectMapper;
  private EventReader eventReader;
  private EventProcessInstanceIndexManager eventBasedProcessIndexManager;

  private final Map<String, EventProcessInstanceImportMediator> importMediators = new ConcurrentHashMap<>();

  public List<EventProcessInstanceImportMediator> getActiveMediators() {
    refreshMediators();
    return importMediators.values()
      .stream().filter(EventProcessInstanceImportMediator::canImport)
      .collect(Collectors.toList());
  }

  private synchronized void refreshMediators() {
    final Map<String, EventProcessPublishStateDto> availableInstanceIndices =
      eventBasedProcessIndexManager.getPublishedInstanceIndices();

    final List<String> removedPublishedIds = importMediators.keySet().stream()
      .filter(publishedStateId -> !availableInstanceIndices.containsKey(publishedStateId))
      .collect(Collectors.toList());
    removedPublishedIds.forEach(publishedStateId -> {
      final EventProcessInstanceImportMediator eventProcessInstanceImportMediator =
        importMediators.get(publishedStateId);
      eventProcessInstanceImportMediator.shutdown();
      importMediators.remove(publishedStateId);
    });

    availableInstanceIndices
      .forEach((publishedId, publishedStateDto) -> {
        if (!importMediators.containsKey(publishedId)) {
          final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
            configurationService
          );
          elasticsearchImportJobExecutor.startExecutingImportJobs();
          importMediators.put(
            publishedId,
            createEventProcessInstanceMediator(publishedId, publishedStateDto, elasticsearchImportJobExecutor)
          );
        }
      });
  }

  private EventProcessInstanceImportMediator createEventProcessInstanceMediator(
      final String instanceIndexId,
      final EventProcessPublishStateDto publishedStateDto,
      final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor) {
    return new EventProcessInstanceImportMediator(
      instanceIndexId,
      configurationService,
      eventReader,
      new EventProcessInstanceImportService(
        publishedStateDto,
        elasticsearchImportJobExecutor,
        new EventProcessInstanceWriter(
          new EventProcessInstanceIndex(instanceIndexId), elasticsearchClient, objectMapper
        )
      )
    );
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    importMediators.clear();
  }
}
