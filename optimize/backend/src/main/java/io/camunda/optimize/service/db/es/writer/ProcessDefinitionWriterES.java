/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_ID;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.TENANT_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ProcessDefinitionWriterES extends AbstractProcessDefinitionWriterES
    implements ProcessDefinitionWriter {

  private static final Script MARK_AS_DELETED_SCRIPT =
      Script.of(i -> i.lang(ScriptLanguage.Painless).source("ctx._source.deleted = true"));

  private static final Script MARK_AS_ONBOARDED_SCRIPT =
      Script.of(i -> i.lang(ScriptLanguage.Painless).source("ctx._source.onboarded = true"));

  private final ConfigurationService configurationService;

  public ProcessDefinitionWriterES(
      final OptimizeElasticsearchClient esClient,
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService,
      final TaskRepositoryES taskRepositoryES) {
    super(objectMapper, esClient, taskRepositoryES);
    this.configurationService = configurationService;
  }

  @Override
  public void importProcessDefinitions(final List<ProcessDefinitionOptimizeDto> procDefs) {
    log.debug("Writing [{}] process definitions to elasticsearch", procDefs.size());
    writeProcessDefinitionInformation(procDefs);
  }

  @Override
  public void markDefinitionAsDeleted(final String definitionId) {
    log.debug("Marking process definition with ID {} as deleted", definitionId);
    try {
      esClient.update(
          new OptimizeUpdateRequestBuilderES<>()
              .optimizeIndex(esClient, PROCESS_DEFINITION_INDEX_NAME)
              .id(definitionId)
              .script(MARK_AS_DELETED_SCRIPT)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
              .build(),
          Object.class);
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(
          String.format(
              "There was a problem when trying to mark process definition with ID %s as deleted",
              definitionId),
          e);
    }
  }

  @Override
  public boolean markRedeployedDefinitionsAsDeleted(
      final List<ProcessDefinitionOptimizeDto> importedDefinitions) {
    final AtomicBoolean definitionsUpdated = new AtomicBoolean(false);
    // We must partition this into batches to avoid the maximum ES boolQuery clause limit being
    // reached
    Lists.partition(importedDefinitions, 1000)
        .forEach(
            partition -> {
              final BoolQuery.Builder definitionsToDeleteQueryBuilder = new BoolQuery.Builder();
              final Set<String> processDefIds = new HashSet<>();
              partition.forEach(
                  definition -> {
                    final BoolQuery.Builder matchingDefinitionQuery =
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
                    processDefIds.add(definition.getId());
                    if (definition.getTenantId() != null) {
                      matchingDefinitionQuery.must(
                          m -> m.term(t -> t.field(TENANT_ID).value(definition.getTenantId())));
                    } else {
                      matchingDefinitionQuery.mustNot(m -> m.exists(t -> t.field(TENANT_ID)));
                    }
                    definitionsToDeleteQueryBuilder.should(
                        s -> s.bool(matchingDefinitionQuery.build()));
                  });
              final boolean deleted =
                  taskRepositoryES.tryUpdateByQueryRequest(
                      String.format("%d process definitions", processDefIds.size()),
                      MARK_AS_DELETED_SCRIPT,
                      Query.of(q -> q.bool(definitionsToDeleteQueryBuilder.build())),
                      PROCESS_DEFINITION_INDEX_NAME);
              if (deleted && !definitionsUpdated.get()) {
                definitionsUpdated.set(true);
              }
            });
    if (definitionsUpdated.get()) {
      log.debug("Marked old process definitions with new deployments as deleted");
    }
    return definitionsUpdated.get();
  }

  @Override
  public void markDefinitionKeysAsOnboarded(final Set<String> definitionKeys) {
    taskRepositoryES.tryUpdateByQueryRequest(
        "process definitions onboarded state",
        MARK_AS_ONBOARDED_SCRIPT,
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.must(
                            m ->
                                m.terms(
                                    t ->
                                        t.field(PROCESS_DEFINITION_KEY)
                                            .terms(
                                                tt ->
                                                    tt.value(
                                                        definitionKeys.stream()
                                                            .map(FieldValue::of)
                                                            .toList())))))),
        PROCESS_DEFINITION_INDEX_NAME);
  }

  @Override
  Script createUpdateScript(final ProcessDefinitionOptimizeDto processDefinitionDto) {
    return ElasticsearchWriterUtil.createFieldUpdateScript(
        FIELDS_TO_UPDATE, processDefinitionDto, objectMapper);
  }

  private void writeProcessDefinitionInformation(
      final List<ProcessDefinitionOptimizeDto> procDefs) {
    final String importItemName = "process definition information";
    log.debug("Writing [{}] {} to ES.", procDefs.size(), importItemName);

    esClient.doImportBulkRequestWithList(
        importItemName,
        procDefs,
        this::addImportProcessDefinitionToRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
