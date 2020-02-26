/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.EventProcessPublishStateReader;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.importing.eventprocess.mediator.EventProcessInstanceImportMediator;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@AllArgsConstructor
@Slf4j
@Component
public class EventProcessInstanceIndexManager implements ConfigurationReloadable {
  final private OptimizeElasticsearchClient elasticsearchClient;
  final private ElasticSearchSchemaManager elasticSearchSchemaManager;
  final private EventProcessPublishStateReader eventProcessPublishStateReader;
  final private OptimizeIndexNameService indexNameService;

  final private Map<String, EventProcessPublishStateDto> publishedInstanceIndices = new ConcurrentHashMap<>();
  final private Map<String, Phaser> indexUsagePhasers = new ConcurrentHashMap<>();

  public synchronized Map<String, EventProcessPublishStateDto> getPublishedInstanceIndices() {
    return publishedInstanceIndices;
  }

  public synchronized void cleanupIndexes() {
    eventProcessPublishStateReader.getAllEventProcessPublishStatesWithDeletedState(true)
      .forEach(publishStateDto -> {
        try {
          final ProcessInstanceIndex processInstanceIndex = new EventProcessInstanceIndex(publishStateDto.getId());
          final boolean indexAlreadyExists = elasticSearchSchemaManager.indexExists(
            elasticsearchClient, processInstanceIndex
          );
          if (indexAlreadyExists) {
            final Phaser phaser = indexUsagePhasers.get(publishStateDto.getId());
            if (phaser == null || phaser.getArrivedParties() == 0) {
              elasticSearchSchemaManager.deleteOptimizeIndex(elasticsearchClient, processInstanceIndex);
            }
          }
        } catch (final Exception e) {
          log.error(
            "Failed cleaning up event process instance index for deleted publish state with id [{}]",
            publishStateDto.getId(),
            e
          );
        }
      });
  }

  public synchronized void syncAvailableIndices() {
    eventProcessPublishStateReader.getAllEventProcessPublishStatesWithDeletedState(true)
      .forEach(publishStateDto -> publishedInstanceIndices.remove(publishStateDto.getId()));

    eventProcessPublishStateReader.getAllEventProcessPublishStatesWithDeletedState(false)
      .forEach(publishStateDto -> {
        try {
          final EventProcessInstanceIndex processInstanceIndex = new EventProcessInstanceIndex(publishStateDto.getId());
          final boolean indexAlreadyExists = elasticSearchSchemaManager.indexExists(
            elasticsearchClient, processInstanceIndex
          );
          if (!indexAlreadyExists) {
            elasticSearchSchemaManager.createOptimizeIndex(
              elasticsearchClient, processInstanceIndex, Collections.singleton(PROCESS_INSTANCE_INDEX_NAME)
            );
          }
          publishedInstanceIndices.putIfAbsent(publishStateDto.getId(), publishStateDto);
        } catch (final Exception e) {
          log.error(
            "Failed ensuring event process instance index is present for definition id [{}]", publishStateDto.getId(), e
          );
        }
      });
  }

  public synchronized CompletableFuture<Void> registerIndexUsageAndReturnCompletableHook(
    final EventProcessInstanceImportMediator eventProcessInstanceImportMediator) {
    final Phaser indexUsagePhaser = indexUsagePhasers.compute(
      eventProcessInstanceImportMediator.getPublishedProcessStateId(),
      (id, existingPhaser) -> {
        if (existingPhaser != null) {
          existingPhaser.register();
          return existingPhaser;
        } else {
          return new Phaser(1);
        }
      }
    );
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    importCompleted.whenComplete((aVoid, throwable) -> {
      indexUsagePhaser.arriveAndDeregister();
    });
    return importCompleted;
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    publishedInstanceIndices.clear();
    indexUsagePhasers.clear();
  }
}
