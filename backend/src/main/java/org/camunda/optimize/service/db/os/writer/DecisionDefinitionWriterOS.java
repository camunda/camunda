/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.writer.DecisionDefinitionWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_ID;
import static org.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.TENANT_ID;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DecisionDefinitionWriterOS implements DecisionDefinitionWriter {

  private final ObjectMapper objectMapper;
  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  private static final Script MARK_AS_DELETED_SCRIPT =
    OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
      "ctx._source.deleted = true",
      Collections.emptyMap()
    );

  @Override
  public void importDecisionDefinitions(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    log.debug("Writing [{}] decision definitions to opensearch", decisionDefinitionOptimizeDtos.size());
    writeDecisionDefinitionInformation(decisionDefinitionOptimizeDtos);
  }

  @Override
  public void markDefinitionAsDeleted(final String definitionId) {
    log.debug("Marking decision definition with ID {} as deleted", definitionId);
    final UpdateRequest.Builder updateRequest = new UpdateRequest.Builder<>()
      .index(DECISION_DEFINITION_INDEX_NAME)
      .id(definitionId)
      .script(MARK_AS_DELETED_SCRIPT)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    String errorMessage = String.format(
      "There was a problem when trying to mark decision definition with ID %s as deleted",
      definitionId
    );
    osClient.update(updateRequest, errorMessage);
  }

  @Override
  public boolean markRedeployedDefinitionsAsDeleted(final List<DecisionDefinitionOptimizeDto> importedDefinitions) {
    final AtomicBoolean definitionsUpdated = new AtomicBoolean(false);
    // We must partition this into batches to avoid the maximum ES boolQuery clause limit being reached
    Lists.partition(importedDefinitions, 1000)
      .forEach(
        partition -> {
          final BoolQuery.Builder definitionsToDeleteQuery = new BoolQuery.Builder();
          final Set<String> decisionDefIds = new HashSet<>();
          partition
            .forEach(definition -> {
              final BoolQuery.Builder matchingDefinitionQuery = new BoolQuery.Builder()
                .must(QueryDSL.term(DECISION_DEFINITION_KEY, definition.getKey()))
                .must(QueryDSL.term(DECISION_DEFINITION_VERSION, definition.getVersion()))
                .mustNot(QueryDSL.term(DECISION_DEFINITION_ID, definition.getId()));

              decisionDefIds.add(definition.getId());

              if (definition.getTenantId() != null) {
                matchingDefinitionQuery.must(QueryDSL.term(TENANT_ID, definition.getTenantId()));
              } else {
                matchingDefinitionQuery.mustNot(QueryDSL.exists(TENANT_ID));
              }
              definitionsToDeleteQuery.should(matchingDefinitionQuery.build()._toQuery());
            });

          final long markedAsDeleted = osClient.updateByQuery(
            DECISION_DEFINITION_INDEX_NAME,
            definitionsToDeleteQuery.build()._toQuery(),
            MARK_AS_DELETED_SCRIPT
          );

          if (markedAsDeleted > 0 && !definitionsUpdated.get()) {
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
    log.debug("Writing [{}] {} to OS.", decisionDefinitionOptimizeDtos.size(), importItemName);
    OpenSearchWriterUtil.doImportBulkRequestWithList(
      osClient,
      importItemName,
      decisionDefinitionOptimizeDtos,
      this::addImportDecisionDefinitionRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached(),
      DECISION_DEFINITION_INDEX_NAME
    );
  }

  private BulkOperation addImportDecisionDefinitionRequest(final DecisionDefinitionOptimizeDto decisionDefinitionDto) {
    final Script updateScript = OpenSearchWriterUtil.createFieldUpdateScript(
      FIELDS_TO_UPDATE,
      decisionDefinitionDto,
      objectMapper
    );

    final UpdateOperation<DecisionDefinitionOptimizeDto> updateOperation =
      new UpdateOperation.Builder<DecisionDefinitionOptimizeDto>()
        .id(decisionDefinitionDto.getId())
        .script(updateScript)
        .upsert(decisionDefinitionDto)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
        .build();

    return new BulkOperation.Builder().update(updateOperation).build();
  }

}
