/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION_TAG;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionWriter {
  private static final Set<String> FIELDS_TO_UPDATE = Set.of(
    DECISION_DEFINITION_KEY,
    DECISION_DEFINITION_VERSION,
    DECISION_DEFINITION_VERSION_TAG,
    DECISION_DEFINITION_NAME,
    DATA_SOURCE,
    TENANT_ID
  );

  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private static final Script MARK_AS_DELETED_SCRIPT = new Script(
    ScriptType.INLINE,
    Script.DEFAULT_SCRIPT_LANG,
    "ctx._source.deleted = true",
    Collections.emptyMap()
  );

  public void importDecisionDefinitions(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    log.debug("Writing [{}] decision definitions to elasticsearch", decisionDefinitionOptimizeDtos.size());
    writeDecisionDefinitionInformation(decisionDefinitionOptimizeDtos);
  }

  public void markDefinitionAsDeleted(final String definitionId) {
    log.debug("Marking decision definition with ID {} as deleted", definitionId);
    try {
      final UpdateRequest updateRequest = new UpdateRequest()
        .index(DECISION_DEFINITION_INDEX_NAME)
        .id(definitionId)
        .script(MARK_AS_DELETED_SCRIPT)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (Exception e) {
      throw new OptimizeRuntimeException(
        String.format("There was a problem when trying to mark decision definition with ID %s as deleted", definitionId),
        e
      );
    }
  }

  public boolean markRedeployedDefinitionsAsDeleted(final List<DecisionDefinitionOptimizeDto> importedDefinitions) {
    final AtomicBoolean definitionsUpdated = new AtomicBoolean(false);
    // We must partition this into batches to avoid the maximum ES boolQuery clause limit being reached
    Lists.partition(importedDefinitions, 1000)
      .forEach(
        partition -> {
          final BoolQueryBuilder definitionsToDeleteQuery = boolQuery();
          final Set<String> decisionDefIds = new HashSet<>();
          partition
            .forEach(definition -> {
              final BoolQueryBuilder matchingDefinitionQuery = boolQuery()
                .must(termQuery(DECISION_DEFINITION_KEY, definition.getKey()))
                .must(termQuery(DECISION_DEFINITION_VERSION, definition.getVersion()))
                .mustNot(termQuery(DECISION_DEFINITION_ID, definition.getId()));
              decisionDefIds.add(definition.getId());
              if (definition.getTenantId() != null) {
                matchingDefinitionQuery.must(termQuery(TENANT_ID, definition.getTenantId()));
              } else {
                matchingDefinitionQuery.mustNot(existsQuery(TENANT_ID));
              }
              definitionsToDeleteQuery.should(matchingDefinitionQuery);
            });

          final boolean deleted = ElasticsearchWriterUtil.tryUpdateByQueryRequest(
            esClient,
            String.format("%d decision definitions", decisionDefIds.size()),
            MARK_AS_DELETED_SCRIPT,
            definitionsToDeleteQuery,
            DECISION_DEFINITION_INDEX_NAME
          );
          if (deleted && !definitionsUpdated.get()) {
            definitionsUpdated.set(true);
          }
        });
    if (definitionsUpdated.get()) {
      log.debug("Marked old decision definitions with new deployments as deleted");
    }
    return definitionsUpdated.get();
  }

  private void writeDecisionDefinitionInformation(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    String importItemName = "decision definition information";
    log.debug("Writing [{}] {} to ES.", decisionDefinitionOptimizeDtos.size(), importItemName);
    ElasticsearchWriterUtil.doImportBulkRequestWithList(
      esClient,
      importItemName,
      decisionDefinitionOptimizeDtos,
      this::addImportDecisionDefinitionRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  private void addImportDecisionDefinitionRequest(final BulkRequest bulkRequest,
                                                  final DecisionDefinitionOptimizeDto decisionDefinitionDto) {
    final Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
      FIELDS_TO_UPDATE,
      decisionDefinitionDto,
      objectMapper
    );
    final UpdateRequest request = new UpdateRequest()
      .index(DECISION_DEFINITION_INDEX_NAME)
      .id(decisionDefinitionDto.getId())
      .script(updateScript)
      .upsert(objectMapper.convertValue(decisionDefinitionDto, Map.class))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }
}
