/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;

import com.google.common.collect.Sets;
import io.camunda.optimize.service.db.EventProcessInstanceIndexManager;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.index.events.EventProcessInstanceIndexES;
import io.camunda.optimize.service.db.reader.EventProcessPublishStateReader;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Conditional(ElasticSearchCondition.class)
public class EventProcessInstanceIndexManagerES extends EventProcessInstanceIndexManager {

  private final OptimizeElasticsearchClient elasticsearchClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;

  public EventProcessInstanceIndexManagerES(
      final OptimizeElasticsearchClient elasticsearchClient,
      final ElasticSearchSchemaManager elasticSearchSchemaManager,
      final EventProcessPublishStateReader eventProcessPublishStateReader,
      final OptimizeIndexNameService indexNameService) {
    super(eventProcessPublishStateReader, indexNameService);
    this.elasticsearchClient = elasticsearchClient;
    this.elasticSearchSchemaManager = elasticSearchSchemaManager;
  }

  @Override
  public synchronized void syncAvailableIndices() {
    eventProcessPublishStateReader
        .getAllEventProcessPublishStatesWithDeletedState(true)
        .forEach(publishStateDto -> publishedInstanceIndices.remove(publishStateDto.getId()));

    eventProcessPublishStateReader
        .getAllEventProcessPublishStatesWithDeletedState(false)
        .forEach(
            publishStateDto -> {
              try {
                final EventProcessInstanceIndexES processInstanceIndex =
                    new EventProcessInstanceIndexES(publishStateDto.getId());
                final boolean indexAlreadyExists =
                    elasticSearchSchemaManager.indexExists(
                        elasticsearchClient, processInstanceIndex);
                if (!indexAlreadyExists) {
                  elasticSearchSchemaManager.createOrUpdateOptimizeIndex(
                      elasticsearchClient,
                      processInstanceIndex,
                      Sets.newHashSet(
                          PROCESS_INSTANCE_MULTI_ALIAS,
                          // additional read alias that matches the non event based
                          // processInstanceIndex naming pattern so we can
                          // read from specific event instance indices without the need to determine
                          // if they are event based
                          indexNameService.getOptimizeIndexAliasForIndex(
                              PROCESS_INSTANCE_INDEX_PREFIX + publishStateDto.getProcessKey())));
                }
                publishedInstanceIndices.putIfAbsent(publishStateDto.getId(), publishStateDto);
              } catch (final Exception e) {
                log.error(
                    "Failed ensuring event process instance index is present for definition id [{}]",
                    publishStateDto.getId(),
                    e);
              }
            });
    cleanupIndexes();
  }

  private synchronized void cleanupIndexes() {
    eventProcessPublishStateReader
        .getAllEventProcessPublishStatesWithDeletedState(true)
        .forEach(
            publishStateDto -> {
              try {
                final ProcessInstanceIndex processInstanceIndex =
                    new EventProcessInstanceIndexES(publishStateDto.getId());
                final boolean indexAlreadyExists =
                    elasticSearchSchemaManager.indexExists(
                        elasticsearchClient, processInstanceIndex);
                if (indexAlreadyExists) {
                  final AtomicInteger usageCount = usageCountPerIndex.get(publishStateDto.getId());
                  if (usageCount == null || usageCount.get() == 0) {
                    elasticSearchSchemaManager.deleteOptimizeIndex(
                        elasticsearchClient, processInstanceIndex);
                  }
                }
              } catch (final Exception e) {
                log.error(
                    "Failed cleaning up event process instance index for deleted publish state with id [{}]",
                    publishStateDto.getId(),
                    e);
              }
            });
  }
}
