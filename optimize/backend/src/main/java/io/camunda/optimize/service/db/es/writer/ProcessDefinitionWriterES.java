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
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDefinitionWriterES extends AbstractProcessDefinitionWriterES
    implements ProcessDefinitionWriter {

  private static final Script MARK_AS_DELETED_SCRIPT =
      new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          "ctx._source.deleted = true",
          Collections.emptyMap());

  private static final Script MARK_AS_ONBOARDED_SCRIPT =
      new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          "ctx._source.onboarded = true",
          Collections.emptyMap());
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ProcessDefinitionWriterES.class);

  private final ConfigurationService configurationService;

  public ProcessDefinitionWriterES(
      final OptimizeElasticsearchClient esClient,
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService) {
    super(objectMapper, esClient);
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
      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(PROCESS_DEFINITION_INDEX_NAME)
              .id(definitionId)
              .script(MARK_AS_DELETED_SCRIPT)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
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
              final BoolQueryBuilder definitionsToDeleteQuery = boolQuery();
              final Set<String> processDefIds = new HashSet<>();
              partition.forEach(
                  definition -> {
                    final BoolQueryBuilder matchingDefinitionQuery =
                        boolQuery()
                            .must(termQuery(PROCESS_DEFINITION_KEY, definition.getKey()))
                            .must(termQuery(PROCESS_DEFINITION_VERSION, definition.getVersion()))
                            .mustNot(termQuery(PROCESS_DEFINITION_ID, definition.getId()));
                    processDefIds.add(definition.getId());
                    if (definition.getTenantId() != null) {
                      matchingDefinitionQuery.must(
                          termQuery(DecisionDefinitionIndex.TENANT_ID, definition.getTenantId()));
                    } else {
                      matchingDefinitionQuery.mustNot(
                          existsQuery(DecisionDefinitionIndex.TENANT_ID));
                    }
                    definitionsToDeleteQuery.should(matchingDefinitionQuery);
                  });
              final boolean deleted =
                  ElasticsearchWriterUtil.tryUpdateByQueryRequest(
                      esClient,
                      String.format("%d process definitions", processDefIds.size()),
                      MARK_AS_DELETED_SCRIPT,
                      definitionsToDeleteQuery,
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
    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient,
        "process definitions onboarded state",
        MARK_AS_ONBOARDED_SCRIPT,
        boolQuery().must(termsQuery(PROCESS_DEFINITION_KEY, definitionKeys)),
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
