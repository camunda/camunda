/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessDefinitionWriterOS extends AbstractProcessDefinitionWriterOS
    implements ProcessDefinitionWriter {

  private static final Script MARK_AS_DELETED_SCRIPT =
      OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
          "ctx._source.deleted = true", Collections.emptyMap());

  private static final Script MARK_AS_ONBOARDED_SCRIPT =
      OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
          "ctx._source.onboarded = true", Collections.emptyMap());
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessDefinitionWriterOS.class);

  private final ConfigurationService configurationService;

  public ProcessDefinitionWriterOS(
      final OptimizeOpenSearchClient osClient,
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService) {
    super(objectMapper, osClient);
    this.configurationService = configurationService;
  }

  @Override
  public void importProcessDefinitions(final List<ProcessDefinitionOptimizeDto> procDefs) {
    LOG.debug("Writing [{}] process definitions to opensearch", procDefs.size());
    writeProcessDefinitionInformation(procDefs);
  }

  @Override
  public void markDefinitionAsDeleted(final String definitionId) {
    LOG.debug("Marking process definition with ID {} as deleted", definitionId);
    final UpdateRequest.Builder updateReqBuilder =
        new UpdateRequest.Builder<>()
            .index(PROCESS_DEFINITION_INDEX_NAME)
            .id(definitionId)
            .script(MARK_AS_DELETED_SCRIPT)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    final String errorMessage =
        String.format(
            "There was a problem when trying to mark process definition with ID %s as deleted",
            definitionId);
    osClient.update(updateReqBuilder, errorMessage);
  }

  @Override
  public boolean markRedeployedDefinitionsAsDeleted(
      final List<ProcessDefinitionOptimizeDto> importedDefinitions) {
    final AtomicBoolean definitionsUpdated = new AtomicBoolean(false);
    // We must partition this into batches to avoid the maximum OS boolQuery clause limit being
    // reached
    // OS counts the clauses in a different way to ES: 300 is roughly equivalent to 1000 in ES
    // The issue was reproduced locally with 300 partition size so reduced to 100
    Lists.partition(importedDefinitions, 100)
        .forEach(
            partition -> {
              final BoolQuery.Builder definitionsToDeleteQuery = new BoolQuery.Builder();
              partition.forEach(
                  definition -> {
                    final BoolQuery.Builder matchingDefinitionQuery =
                        new BoolQuery.Builder()
                            .must(QueryDSL.term(PROCESS_DEFINITION_KEY, definition.getKey()))
                            .must(
                                QueryDSL.term(PROCESS_DEFINITION_VERSION, definition.getVersion()))
                            .mustNot(QueryDSL.term(PROCESS_DEFINITION_ID, definition.getId()));
                    if (definition.getTenantId() != null) {
                      matchingDefinitionQuery.must(
                          QueryDSL.term(
                              DecisionDefinitionIndex.TENANT_ID, definition.getTenantId()));
                    } else {
                      matchingDefinitionQuery.mustNot(
                          QueryDSL.exists(DecisionDefinitionIndex.TENANT_ID));
                    }
                    definitionsToDeleteQuery.should(matchingDefinitionQuery.build().toQuery());
                  });

              final long deleted =
                  osClient.updateByQuery(
                      PROCESS_DEFINITION_INDEX_NAME,
                      definitionsToDeleteQuery.build().toQuery(),
                      MARK_AS_DELETED_SCRIPT);

              if (deleted > 0 && !definitionsUpdated.get()) {
                definitionsUpdated.set(true);
              }
            });
    if (definitionsUpdated.get()) {
      LOG.debug("Marked old process definitions with new deployments as deleted");
    }
    return definitionsUpdated.get();
  }

  @Override
  public void markDefinitionKeysAsOnboarded(final Set<String> definitionKeys) {
    osClient.updateByQuery(
        PROCESS_DEFINITION_INDEX_NAME,
        new BoolQuery.Builder()
            .must(QueryDSL.terms(PROCESS_DEFINITION_KEY, definitionKeys, FieldValue::of))
            .build()
            .toQuery(),
        MARK_AS_ONBOARDED_SCRIPT);
  }

  @Override
  Script createUpdateScript(final ProcessDefinitionOptimizeDto processDefinitionDto) {
    return OpenSearchWriterUtil.createFieldUpdateScript(
        FIELDS_TO_UPDATE, processDefinitionDto, objectMapper);
  }

  private void writeProcessDefinitionInformation(
      final List<ProcessDefinitionOptimizeDto> procDefs) {
    final String importItemName = "process definition information";
    LOG.debug("Writing [{}] {} to OpenSearch.", procDefs.size(), importItemName);

    osClient.doImportBulkRequestWithList(
        importItemName,
        procDefs,
        this::addImportProcessDefinitionToRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached(),
        PROCESS_DEFINITION_INDEX_NAME);
  }
}
