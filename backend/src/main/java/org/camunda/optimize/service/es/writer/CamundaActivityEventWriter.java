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
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@AllArgsConstructor
@Component
@Slf4j
public class CamundaActivityEventWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final ObjectMapper objectMapper;

  public List<ImportRequestDto> generateImportRequests(List<CamundaActivityEventDto> camundaActivityEvents) {
    String importItemName = "camunda activity events";
    log.debug("Creating imports for {} [{}].", camundaActivityEvents.size(), importItemName);

    final List<String> processDefinitionKeysInBatch = camundaActivityEvents
      .stream()
      .map(CamundaActivityEventDto::getProcessDefinitionKey)
      .collect(Collectors.toList());
    createMissingActivityIndicesForProcessDefinitions(processDefinitionKeysInBatch);

    return camundaActivityEvents.stream()
      .map(this::createIndexRequestForActivityEvent)
      .filter(Optional::isPresent)
      .map(request -> ImportRequestDto.builder()
        .importName(importItemName)
        .esClient(esClient)
        .request(request.get())
        .build())
      .filter(importRequest -> Objects.nonNull(importRequest.getRequest()))
      .collect(Collectors.toList());
  }

  public void deleteByProcessInstanceIds(final String definitionKey, final List<String> processInstanceIds) {
    final String deletedItemName = "variable updates";
    log.debug("Deleting camunda activity events for [{}] processInstanceIds", processInstanceIds.size());

    final BoolQueryBuilder filterQuery = boolQuery()
      .filter(termsQuery(CamundaActivityEventIndex.PROCESS_INSTANCE_ID, processInstanceIds));

    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
      esClient,
      filterQuery,
      deletedItemName,
      "list of ids",
      false,
      // use wildcarded index name to catch all indices that exist after potential rollover
      esClient.getIndexNameService()
        .getOptimizeIndexNameWithVersionWithWildcardSuffix(new CamundaActivityEventIndex(definitionKey))
    );
  }

  private Optional<IndexRequest> createIndexRequestForActivityEvent(CamundaActivityEventDto camundaActivityEventDto) {
    try {
      return Optional.of(
        new IndexRequest(new CamundaActivityEventIndex(camundaActivityEventDto.getProcessDefinitionKey()).getIndexName())
          .id(IdGenerator.getNextId())
          .source(objectMapper.writeValueAsString(camundaActivityEventDto), XContentType.JSON)
      );
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize Camunda Activity Event: {}", camundaActivityEventDto, e);
      return Optional.empty();
    }
  }

  private void createMissingActivityIndicesForProcessDefinitions(List<String> processDefinitionKeys) {
    final List<IndexMappingCreator> activityIndicesToCheck = processDefinitionKeys.stream()
      .distinct()
      .map(CamundaActivityEventIndex::new)
      .collect(Collectors.toList());
    if (!activityIndicesToCheck.isEmpty()) {
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
      activityIndicesToCheck.forEach(
        activityIndex -> elasticSearchSchemaManager.createIndexIfMissing(esClient, activityIndex)
      );
    }
  }

}
