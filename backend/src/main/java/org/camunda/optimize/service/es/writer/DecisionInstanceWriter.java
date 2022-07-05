/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionInstanceWriter implements ConfigurationReloadable {

  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;

  private final Set<String> existingInstanceIndexDefinitionKeys = ConcurrentHashMap.newKeySet();

  public void importDecisionInstances(List<DecisionInstanceDto> decisionInstanceDtos) {
    final String importItemName = "decision instances";
    log.debug("Writing [{}] {} to ES.", decisionInstanceDtos.size(), importItemName);
    createInstanceIndicesIfMissing(decisionInstanceDtos);
    ElasticsearchWriterUtil.doImportBulkRequestWithList(
      esClient,
      importItemName,
      decisionInstanceDtos,
      this::addImportDecisionInstanceRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  public void deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(final String decisionDefinitionKey,
                                                                               final OffsetDateTime evaluationDate) {
    if (!indexExists(decisionDefinitionKey)) {
      log.info(
        "Aborting deletion of instances of definition with key {} because no instances exist for this definition.",
        decisionDefinitionKey
      );
      return;
    }
    final EsBulkByScrollTaskActionProgressReporter progressReporter = new EsBulkByScrollTaskActionProgressReporter(
      getClass().getName(), esClient, DeleteByQueryAction.NAME
    );
    try {
      progressReporter.start();
      final BoolQueryBuilder filterQuery = boolQuery()
        .filter(rangeQuery(DecisionInstanceIndex.EVALUATION_DATE_TIME).lt(dateTimeFormatter.format(evaluationDate)));

      ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        String.format(
          "decision instances with definitionKey %s and evaluationDate past %s",
          decisionDefinitionKey,
          evaluationDate
        ),
        true,
        getDecisionInstanceIndexAliasName((decisionDefinitionKey))
      );
    } finally {
      progressReporter.stop();
    }
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    existingInstanceIndexDefinitionKeys.clear();
  }

  private void addImportDecisionInstanceRequest(final BulkRequest bulkRequest,
                                                final DecisionInstanceDto decisionInstanceDto) {
    final String decisionInstanceId = decisionInstanceDto.getDecisionInstanceId();
    String source = "";
    try {
      source = objectMapper.writeValueAsString(decisionInstanceDto);
    } catch (JsonProcessingException e) {
      final String reason = String.format(
        "Error while processing JSON for decision instance DTO with ID [%s].",
        decisionInstanceDto.getDecisionInstanceId()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    final IndexRequest request =
      new IndexRequest(getDecisionInstanceIndexAliasName(decisionInstanceDto.getDecisionDefinitionKey()))
        .id(decisionInstanceId)
        .source(source, XContentType.JSON);

    bulkRequest.add(request);
  }

  private void createInstanceIndicesIfMissing(final List<DecisionInstanceDto> decisionInstanceDtos) {
    final Set<String> decisionDefinitionKeys = decisionInstanceDtos.stream()
      .map(DecisionInstanceDto::getDecisionDefinitionKey)
      .collect(toSet());
    decisionDefinitionKeys.removeIf(this::indexExists);
    if (!decisionDefinitionKeys.isEmpty()) {
      createMissingInstanceIndices(decisionDefinitionKeys);
    }
  }

  private void createMissingInstanceIndices(final Set<String> defKeysOfMissingIndices) {
    log.debug("Creating decision instance index for definition keys [{}].", defKeysOfMissingIndices);
    defKeysOfMissingIndices.forEach(defKey -> elasticSearchSchemaManager.createOrUpdateOptimizeIndex(
      esClient,
      new DecisionInstanceIndex(defKey),
      Collections.singleton(DECISION_INSTANCE_MULTI_ALIAS)
    ));
    existingInstanceIndexDefinitionKeys.addAll(defKeysOfMissingIndices);
  }

  private boolean indexExists(final String definitionKey) {
    return existingInstanceIndexDefinitionKeys.contains(definitionKey)
      || elasticSearchSchemaManager.indexExists(esClient, getDecisionInstanceIndexAliasName(definitionKey));
  }
}