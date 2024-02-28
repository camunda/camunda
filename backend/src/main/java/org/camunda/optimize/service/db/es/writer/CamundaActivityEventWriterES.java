/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.db.es.schema.index.events.CamundaActivityEventIndexES;
import org.camunda.optimize.service.db.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.db.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.db.writer.CamundaActivityEventWriter;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class CamundaActivityEventWriterES implements CamundaActivityEventWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final ObjectMapper objectMapper;
  private final CamundaActivityEventReader camundaActivityEventReader;

  @Override
  public List<ImportRequestDto> generateImportRequests(
      List<CamundaActivityEventDto> camundaActivityEvents) {
    String importItemName = "camunda activity events";
    log.debug("Creating imports for {} [{}].", camundaActivityEvents.size(), importItemName);

    final List<String> processDefinitionKeysInBatch =
        camundaActivityEvents.stream()
            .map(CamundaActivityEventDto::getProcessDefinitionKey)
            .collect(Collectors.toList());

    createMissingActivityIndicesForProcessDefinitions(processDefinitionKeysInBatch);

    return camundaActivityEvents.stream()
        .map(entry -> createIndexRequestForActivityEvent(entry, importItemName))
        .toList();
  }

  @Override
  public void deleteByProcessInstanceIds(
      final String definitionKey, final List<String> processInstanceIds) {
    log.debug(
        "Deleting camunda activity events for [{}] processInstanceIds", processInstanceIds.size());

    final BoolQueryBuilder filterQuery =
        boolQuery()
            .filter(termsQuery(CamundaActivityEventIndex.PROCESS_INSTANCE_ID, processInstanceIds));

    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        String.format("camunda activity events of %d process instances", processInstanceIds.size()),
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        esClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new CamundaActivityEventIndexES(definitionKey)));
  }

  private ImportRequestDto createIndexRequestForActivityEvent(
      CamundaActivityEventDto camundaActivityEventDto, final String importName) {
    return ImportRequestDto.builder()
        .indexName(
            CamundaActivityEventIndex.constructIndexName(
                camundaActivityEventDto.getProcessDefinitionKey()))
        .id(IdGenerator.getNextId())
        .type(RequestType.INDEX)
        .source(camundaActivityEventDto)
        .importName(importName)
        .build();
  }

  private void createMissingActivityIndicesForProcessDefinitions(
      List<String> processDefinitionKeys) {
    final Set<String> currentProcessDefinitions =
        camundaActivityEventReader.getIndexSuffixesForCurrentActivityIndices();
    processDefinitionKeys.removeAll(currentProcessDefinitions);
    processDefinitionKeys.stream()
        .distinct()
        .map(CamundaActivityEventIndexES::new)
        .forEach(
            activityIndex ->
                elasticSearchSchemaManager.createIndexIfMissing(esClient, activityIndex));
  }
}
