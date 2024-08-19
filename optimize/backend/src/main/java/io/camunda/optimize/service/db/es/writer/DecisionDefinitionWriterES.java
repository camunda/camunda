/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_ID;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.TENANT_ID;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.writer.DecisionDefinitionWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionDefinitionWriterES implements DecisionDefinitionWriter {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(DecisionDefinitionWriterES.class);
  private static final Script MARK_AS_DELETED_SCRIPT =
      new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          "ctx._source.deleted = true",
          Collections.emptyMap());
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;

  public DecisionDefinitionWriterES(
      final ObjectMapper objectMapper,
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService) {
    this.objectMapper = objectMapper;
    this.esClient = esClient;
    this.configurationService = configurationService;
  }

  @Override
  public void importDecisionDefinitions(
      final List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    log.debug(
        "Writing [{}] decision definitions to elasticsearch",
        decisionDefinitionOptimizeDtos.size());
    writeDecisionDefinitionInformation(decisionDefinitionOptimizeDtos);
  }

  @Override
  public void markDefinitionAsDeleted(final String definitionId) {
    log.debug("Marking decision definition with ID {} as deleted", definitionId);
    try {
      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(DECISION_DEFINITION_INDEX_NAME)
              .id(definitionId)
              .script(MARK_AS_DELETED_SCRIPT)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(
          String.format(
              "There was a problem when trying to mark decision definition with ID %s as deleted",
              definitionId),
          e);
    }
  }

  @Override
  public boolean markRedeployedDefinitionsAsDeleted(
      final List<DecisionDefinitionOptimizeDto> importedDefinitions) {
    final AtomicBoolean definitionsUpdated = new AtomicBoolean(false);
    // We must partition this into batches to avoid the maximum ES boolQuery clause limit being
    // reached
    Lists.partition(importedDefinitions, 1000)
        .forEach(
            partition -> {
              final BoolQueryBuilder definitionsToDeleteQuery = boolQuery();
              final Set<String> decisionDefIds = new HashSet<>();
              partition.forEach(
                  definition -> {
                    final BoolQueryBuilder matchingDefinitionQuery =
                        boolQuery()
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

              final boolean deleted =
                  ElasticsearchWriterUtil.tryUpdateByQueryRequest(
                      esClient,
                      String.format("%d decision definitions", decisionDefIds.size()),
                      MARK_AS_DELETED_SCRIPT,
                      definitionsToDeleteQuery,
                      DECISION_DEFINITION_INDEX_NAME);
              if (deleted && !definitionsUpdated.get()) {
                definitionsUpdated.set(true);
              }
            });
    if (definitionsUpdated.get()) {
      log.debug("Marked old decision definitions with new deployments as deleted");
    }
    return definitionsUpdated.get();
  }

  private void writeDecisionDefinitionInformation(
      final List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    final String importItemName = "decision definition information";
    log.debug("Writing [{}] {} to ES.", decisionDefinitionOptimizeDtos.size(), importItemName);
    esClient.doImportBulkRequestWithList(
        importItemName,
        decisionDefinitionOptimizeDtos,
        this::addImportDecisionDefinitionRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  private void addImportDecisionDefinitionRequest(
      final BulkRequest bulkRequest, final DecisionDefinitionOptimizeDto decisionDefinitionDto) {
    final Script updateScript =
        ElasticsearchWriterUtil.createFieldUpdateScript(
            FIELDS_TO_UPDATE, decisionDefinitionDto, objectMapper);
    final UpdateRequest request =
        new UpdateRequest()
            .index(DECISION_DEFINITION_INDEX_NAME)
            .id(decisionDefinitionDto.getId())
            .script(updateScript)
            .upsert(objectMapper.convertValue(decisionDefinitionDto, Map.class))
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }
}
