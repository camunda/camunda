/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;

import com.google.common.collect.Sets;
import io.camunda.optimize.service.db.EventProcessInstanceIndexManager;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.os.schema.index.events.EventProcessInstanceIndexOS;
import io.camunda.optimize.service.db.reader.EventProcessPublishStateReader;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Conditional(OpenSearchCondition.class)
public class EventProcessInstanceIndexManagerOS extends EventProcessInstanceIndexManager {

  private final OptimizeOpenSearchClient optimizeOpenSearchClient;
  private final OpenSearchSchemaManager openSearchSchemaManager;

  public EventProcessInstanceIndexManagerOS(
      final OptimizeOpenSearchClient optimizeOpenSearchClient,
      final OpenSearchSchemaManager openSearchSchemaManager,
      final EventProcessPublishStateReader eventProcessPublishStateReader,
      final OptimizeIndexNameService indexNameService) {
    super(eventProcessPublishStateReader, indexNameService);
    this.optimizeOpenSearchClient = optimizeOpenSearchClient;
    this.openSearchSchemaManager = openSearchSchemaManager;
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
                final EventProcessInstanceIndexOS processInstanceIndex =
                    new EventProcessInstanceIndexOS(publishStateDto.getId());
                final boolean indexAlreadyExists =
                    openSearchSchemaManager.indexExists(
                        optimizeOpenSearchClient, processInstanceIndex);
                if (!indexAlreadyExists) {
                  openSearchSchemaManager.createOrUpdateOptimizeIndex(
                      optimizeOpenSearchClient,
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
    cleanupIndexOS();
  }

  private synchronized void cleanupIndexOS() {
    eventProcessPublishStateReader
        .getAllEventProcessPublishStatesWithDeletedState(true)
        .forEach(
            publishStateDto -> {
              try {
                final ProcessInstanceIndex processInstanceIndex =
                    new EventProcessInstanceIndexOS(publishStateDto.getId());
                final boolean indexAlreadyExists =
                    openSearchSchemaManager.indexExists(
                        optimizeOpenSearchClient, processInstanceIndex);
                if (indexAlreadyExists) {
                  final AtomicInteger usageCount = usageCountPerIndex.get(publishStateDto.getId());
                  if (usageCount == null || usageCount.get() == 0) {
                    openSearchSchemaManager.deleteOptimizeIndex(
                        optimizeOpenSearchClient, processInstanceIndex);
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
