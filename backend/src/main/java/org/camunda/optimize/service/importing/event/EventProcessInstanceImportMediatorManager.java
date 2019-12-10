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
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.events.EventService;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class EventProcessInstanceImportMediatorManager implements ConfigurationReloadable {
  private final ConfigurationService configurationService;
  private final OptimizeElasticsearchClient elasticsearchClient;
  private final ObjectMapper objectMapper;
  private final EventService eventService;
  private final EventProcessInstanceIndexManager eventBasedProcessIndexManager;

  private final Map<String, EventProcessInstanceImportMediator> importMediators = new ConcurrentHashMap<>();

  public Optional<EventProcessInstanceImportMediator> getMediatorByEventProcessPublishId(final String id) {
    return Optional.ofNullable(importMediators.get(id));
  }

  public Collection<EventProcessInstanceImportMediator> getActiveMediators() {
    return importMediators.values();
  }

  public synchronized void refreshMediators() {
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
      .entrySet()
      .stream()
      .filter(entry -> !importMediators.containsKey(entry.getKey()))
      .forEach(entry -> {
        final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
          configurationService
        );
        elasticsearchImportJobExecutor.startExecutingImportJobs();
        importMediators.put(
          entry.getKey(),
          createEventProcessInstanceMediator(entry.getKey(), entry.getValue(), elasticsearchImportJobExecutor)
        );
      });
  }

  private EventProcessInstanceImportMediator createEventProcessInstanceMediator(
    final String instanceIndexId,
    final EventProcessPublishStateDto publishedStateDto,
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor) {
    return new EventProcessInstanceImportMediator(
      instanceIndexId,
      configurationService,
      eventService,
      publishedStateDto.getLastImportedEventIngestDateTime().toInstant().toEpochMilli(),
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
