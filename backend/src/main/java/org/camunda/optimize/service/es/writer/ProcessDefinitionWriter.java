/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION_TAG;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
@Slf4j
public class ProcessDefinitionWriter extends AbstractProcessDefinitionWriter {
  private static final Set<String> FIELDS_TO_UPDATE = Set.of(
    PROCESS_DEFINITION_KEY,
    PROCESS_DEFINITION_VERSION,
    PROCESS_DEFINITION_VERSION_TAG,
    PROCESS_DEFINITION_NAME,
    DATA_SOURCE,
    TENANT_ID
  );

  private static final Script MARK_AS_DELETED_SCRIPT = new Script(
    ScriptType.INLINE,
    Script.DEFAULT_SCRIPT_LANG,
    "ctx._source.deleted = true",
    Collections.emptyMap()
  );

  private static final Script MARK_AS_ONBOARDED_SCRIPT = new Script(
    ScriptType.INLINE,
    Script.DEFAULT_SCRIPT_LANG,
    "ctx._source.onboarded = true",
    Collections.emptyMap()
  );

  private final ConfigurationService configurationService;

  public ProcessDefinitionWriter(final OptimizeElasticsearchClient esClient,
                                 final ObjectMapper objectMapper,
                                 final ConfigurationService configurationService) {
    super(objectMapper, esClient);
    this.configurationService = configurationService;
  }

  public void importProcessDefinitions(List<ProcessDefinitionOptimizeDto> procDefs) {
    log.debug("Writing [{}] process definitions to elasticsearch", procDefs.size());
    writeProcessDefinitionInformation(procDefs);
  }

  public void markDefinitionAsDeleted(final String definitionId) {
    log.debug("Marking process definition with ID {} as deleted", definitionId);
    try {
      final UpdateRequest updateRequest = new UpdateRequest()
        .index(PROCESS_DEFINITION_INDEX_NAME)
        .id(definitionId)
        .script(MARK_AS_DELETED_SCRIPT)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (Exception e) {
      throw new OptimizeRuntimeException(
        String.format("There was a problem when trying to mark process definition with ID %s as deleted", definitionId),
        e
      );
    }
  }

  public boolean markRedeployedDefinitionsAsDeleted(final List<ProcessDefinitionOptimizeDto> importedDefinitions) {
    final AtomicBoolean definitionsUpdated = new AtomicBoolean(false);
    // We must partition this into batches to avoid the maximum ES boolQuery clause limit being reached
    Lists.partition(importedDefinitions, 1000)
      .forEach(
        partition -> {
          final BoolQueryBuilder definitionsToDeleteQuery = boolQuery();
          final Set<String> processDefIds = new HashSet<>();
          partition
            .forEach(definition -> {
              final BoolQueryBuilder matchingDefinitionQuery = boolQuery()
                .must(termQuery(PROCESS_DEFINITION_KEY, definition.getKey()))
                .must(termQuery(PROCESS_DEFINITION_VERSION, definition.getVersion()))
                .mustNot(termQuery(PROCESS_DEFINITION_ID, definition.getId()));
              processDefIds.add(definition.getId());
              if (definition.getTenantId() != null) {
                matchingDefinitionQuery.must(termQuery(
                  DecisionDefinitionIndex.TENANT_ID,
                  definition.getTenantId()
                ));
              } else {
                matchingDefinitionQuery.mustNot(existsQuery(DecisionDefinitionIndex.TENANT_ID));
              }
              definitionsToDeleteQuery.should(matchingDefinitionQuery);
            });
          final boolean deleted = ElasticsearchWriterUtil.tryUpdateByQueryRequest(
            esClient,
            String.format("%d process definitions", processDefIds.size()),
            MARK_AS_DELETED_SCRIPT,
            definitionsToDeleteQuery,
            PROCESS_DEFINITION_INDEX_NAME
          );
          if (deleted && !definitionsUpdated.get()) {
            definitionsUpdated.set(true);
          }
        }
      );
    if (definitionsUpdated.get()) {
      log.debug("Marked old process definitions with new deployments as deleted");
    }
    return definitionsUpdated.get();
  }

  public void markDefinitionKeysAsOnboarded(final Set<String> definitionKeys) {
    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
      esClient,
      "process definitions onboarded state",
      MARK_AS_ONBOARDED_SCRIPT,
      boolQuery().must(termsQuery(PROCESS_DEFINITION_KEY, definitionKeys)),
      PROCESS_DEFINITION_INDEX_NAME
    );
  }

  @Override
  Script createUpdateScript(final ProcessDefinitionOptimizeDto processDefinitionDto) {
    return ElasticsearchWriterUtil.createFieldUpdateScript(FIELDS_TO_UPDATE, processDefinitionDto, objectMapper);
  }

  private void writeProcessDefinitionInformation(List<ProcessDefinitionOptimizeDto> procDefs) {
    String importItemName = "process definition information";
    log.debug("Writing [{}] {} to ES.", procDefs.size(), importItemName);

    ElasticsearchWriterUtil.doImportBulkRequestWithList(
      esClient,
      importItemName,
      procDefs,
      this::addImportProcessDefinitionToRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }
}
