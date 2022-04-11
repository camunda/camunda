/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.EventProcessPublishStateReader;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

@AllArgsConstructor
@Slf4j
@Component
public class EventProcessInstanceIndexManager implements ConfigurationReloadable {
  private final OptimizeElasticsearchClient elasticsearchClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final EventProcessPublishStateReader eventProcessPublishStateReader;
  private final OptimizeIndexNameService indexNameService;

  private final Map<String, EventProcessPublishStateDto> publishedInstanceIndices = new HashMap<>();
  private final Map<String, AtomicInteger> usageCountPerIndex = new HashMap<>();

  public synchronized Map<String, EventProcessPublishStateDto> getPublishedInstanceStatesMap() {
    return publishedInstanceIndices;
  }

  public synchronized Collection<EventProcessPublishStateDto> getPublishedInstanceStates() {
    return publishedInstanceIndices.values();
  }

  public synchronized void syncAvailableIndices() {
    eventProcessPublishStateReader.getAllEventProcessPublishStatesWithDeletedState(true)
      .forEach(publishStateDto -> publishedInstanceIndices.remove(publishStateDto.getId()));

    eventProcessPublishStateReader.getAllEventProcessPublishStatesWithDeletedState(false)
      .forEach(publishStateDto -> {
        try {
          final EventProcessInstanceIndex processInstanceIndex = new EventProcessInstanceIndex(publishStateDto.getId());
          final boolean indexAlreadyExists =
            elasticSearchSchemaManager.indexExists(elasticsearchClient, processInstanceIndex);
          if (!indexAlreadyExists) {
            elasticSearchSchemaManager.createOrUpdateOptimizeIndex(
              elasticsearchClient,
              processInstanceIndex,
              Sets.newHashSet(
                PROCESS_INSTANCE_MULTI_ALIAS,
                // additional read alias that matches the non event based processInstanceIndex naming pattern so we can
                // read from specific event instance indices without the need to determine if they are event based
                indexNameService.getOptimizeIndexAliasForIndex(PROCESS_INSTANCE_INDEX_PREFIX + publishStateDto.getProcessKey())
              )
            );
          }
          publishedInstanceIndices.putIfAbsent(publishStateDto.getId(), publishStateDto);
        } catch (final Exception e) {
          log.error(
            "Failed ensuring event process instance index is present for definition id [{}]", publishStateDto.getId(), e
          );
        }
      });
    cleanupIndexes();
  }

  private synchronized void cleanupIndexes() {
    eventProcessPublishStateReader.getAllEventProcessPublishStatesWithDeletedState(true)
      .forEach(publishStateDto -> {
        try {
          final ProcessInstanceIndex processInstanceIndex = new EventProcessInstanceIndex(publishStateDto.getId());
          final boolean indexAlreadyExists = elasticSearchSchemaManager.indexExists(
            elasticsearchClient, processInstanceIndex
          );
          if (indexAlreadyExists) {
            final AtomicInteger usageCount = usageCountPerIndex.get(publishStateDto.getId());
            if (usageCount == null || usageCount.get() == 0) {
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

  public synchronized CompletableFuture<Void> registerIndexUsageAndReturnFinishedHandler(
    final String eventProcessPublishStateId) {
    final AtomicInteger indexUsageCounter = usageCountPerIndex.compute(
      eventProcessPublishStateId,
      (id, usageCounter) -> {
        if (usageCounter != null) {
          usageCounter.incrementAndGet();
          return usageCounter;
        } else {
          return new AtomicInteger(1);
        }
      }
    );
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    importCompleted.whenComplete((aVoid, throwable) -> indexUsageCounter.decrementAndGet());
    return importCompleted;
  }

  @Override
  public synchronized void reloadConfiguration(final ApplicationContext context) {
    publishedInstanceIndices.clear();
    usageCountPerIndex.clear();
  }
}
