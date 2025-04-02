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

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateOperationBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.writer.DecisionDefinitionWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class DecisionDefinitionWriterES implements DecisionDefinitionWriter {

  private static final Script MARK_AS_DELETED_SCRIPT =
      Script.of(i -> i.lang(ScriptLanguage.Painless).source("ctx._source.deleted = true"));
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final TaskRepositoryES taskRepositoryES;

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
      esClient.update(
          new OptimizeUpdateRequestBuilderES<>()
              .optimizeIndex(esClient, DECISION_DEFINITION_INDEX_NAME)
              .id(definitionId)
              .script(MARK_AS_DELETED_SCRIPT)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
              .build(),
          Object.class);
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
              final BoolQuery.Builder definitionsToDeleteQueryBuilder = new BoolQuery.Builder();
              final Set<String> decisionDefIds = new HashSet<>();
              partition.forEach(
                  definition -> {
                    final BoolQuery.Builder matchingDefinitionQueryBuilder =
                        new BoolQuery.Builder()
                            .must(
                                m ->
                                    m.term(
                                        t ->
                                            t.field(DECISION_DEFINITION_KEY)
                                                .value(definition.getKey())))
                            .must(
                                m ->
                                    m.term(
                                        t ->
                                            t.field(DECISION_DEFINITION_VERSION)
                                                .value(definition.getVersion())))
                            .mustNot(
                                m ->
                                    m.term(
                                        t ->
                                            t.field(DECISION_DEFINITION_ID)
                                                .value(definition.getId())));
                    decisionDefIds.add(definition.getId());
                    if (definition.getTenantId() != null) {
                      matchingDefinitionQueryBuilder.must(
                          m -> m.term(t -> t.field(TENANT_ID).value(definition.getTenantId())));
                    } else {
                      matchingDefinitionQueryBuilder.mustNot(
                          m -> m.exists(t -> t.field(TENANT_ID)));
                    }
                    definitionsToDeleteQueryBuilder.should(
                        s -> s.bool(matchingDefinitionQueryBuilder.build()));
                  });

              final boolean deleted =
                  taskRepositoryES.tryUpdateByQueryRequest(
                      String.format("%d decision definitions", decisionDefIds.size()),
                      MARK_AS_DELETED_SCRIPT,
                      Query.of(q -> q.bool(definitionsToDeleteQueryBuilder.build())),
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
      final BulkRequest.Builder bulkRequestBuilder,
      final DecisionDefinitionOptimizeDto decisionDefinitionDto) {
    final Script updateScript =
        ElasticsearchWriterUtil.createFieldUpdateScript(
            FIELDS_TO_UPDATE, decisionDefinitionDto, objectMapper);

    bulkRequestBuilder.operations(
        o ->
            o.update(
                OptimizeUpdateOperationBuilderES.of(
                    u ->
                        u.optimizeIndex(esClient, DECISION_DEFINITION_INDEX_NAME)
                            .id(decisionDefinitionDto.getId())
                            .action(
                                a ->
                                    a.script(updateScript)
                                        .upsert(
                                            objectMapper.convertValue(
                                                decisionDefinitionDto, Map.class)))
                            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT))));
  }
}
