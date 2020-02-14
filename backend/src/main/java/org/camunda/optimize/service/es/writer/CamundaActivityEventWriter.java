/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.CamundaActivityEventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;

@AllArgsConstructor
@Component
@Slf4j
public class CamundaActivityEventWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final ObjectMapper objectMapper;

  public void importCamundaActivityEvents(List<CamundaActivityEventDto> camundaActivityEvents) {
    String importItemName = "camunda activity events";
    log.debug("Writing [{}] {} to ES.", camundaActivityEvents.size(), importItemName);

    final List<String> processDefinitionKeysInBatch = camundaActivityEvents
      .stream()
      .map(CamundaActivityEventDto::getProcessDefinitionKey)
      .collect(Collectors.toList());
    createMissingActivityIndicesForProcessDefinitions(processDefinitionKeysInBatch);

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      camundaActivityEvents,
      this::addActivityInstancesToCamundaActivityEvents
    );
  }

  private void addActivityInstancesToCamundaActivityEvents(BulkRequest addCompletedActivityInstancesBulkRequest,
                                                           CamundaActivityEventDto camundaActivityEventDto) {
    try {
      final IndexRequest request = new IndexRequest(
        CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + camundaActivityEventDto.getProcessDefinitionKey()
      )
        .id(IdGenerator.getNextId())
        .source(objectMapper.writeValueAsString(camundaActivityEventDto), XContentType.JSON);
      addCompletedActivityInstancesBulkRequest.add(request);
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize Camunda Activity Event: {}", camundaActivityEventDto, e);
    }
  }

  private void createMissingActivityIndicesForProcessDefinitions(List<String> processDefinitionKeys) {
    final List<IndexMappingCreator> activityIndicesToCheck = processDefinitionKeys.stream()
      .distinct()
      .map(CamundaActivityEventIndex::new)
      .collect(Collectors.toList());
    try {
      // We make this check first to see if we can avoid checking individually for each definition key in the batch
      if (elasticSearchSchemaManager.indicesExist(esClient, activityIndicesToCheck)) {
        return;
      }
    } catch (OptimizeRuntimeException ex) {
      log.warn(
        "Failed to check if camunda activity event indices exist for process definition keys {}",
        processDefinitionKeys
      );
    }
    activityIndicesToCheck.forEach(activityIndex -> {
      try {
        final boolean indexAlreadyExists = elasticSearchSchemaManager.indexExists(
          esClient, activityIndex
        );
        if (!indexAlreadyExists) {
          elasticSearchSchemaManager.createOptimizeIndex(esClient, activityIndex);
        }
      } catch (final Exception e) {
        log.error("Failed ensuring camunda activity event index is present: {}", activityIndex.getIndexName(), e);
        throw e;
      }
    });
  }

}
