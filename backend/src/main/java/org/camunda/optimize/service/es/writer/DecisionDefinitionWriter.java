/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
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

import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION_TAG;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.ENGINE;
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
  private static final Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(
    DECISION_DEFINITION_KEY,
    DECISION_DEFINITION_VERSION,
    DECISION_DEFINITION_VERSION_TAG,
    DECISION_DEFINITION_NAME,
    ENGINE,
    TENANT_ID
  );

  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private static final Script MARK_AS_DELETED_SCRIPT = new Script(
    ScriptType.INLINE,
    Script.DEFAULT_SCRIPT_LANG,
    "ctx._source.deleted = true",
    Collections.emptyMap()
  );

  public void importProcessDefinitions(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    log.debug("Writing [{}] decision definitions to elasticsearch", decisionDefinitionOptimizeDtos.size());
    writeDecisionDefinitionInformation(decisionDefinitionOptimizeDtos);
  }

  public boolean markRedeployedDefinitionsAsDeleted(final List<DecisionDefinitionOptimizeDto> importedDefinitions) {
    final BoolQueryBuilder definitionsToDeleteQuery = boolQuery();
    final Set<String> decisionDefIds = new HashSet<>();
    importedDefinitions
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

    final boolean definitionsUpdated = ElasticsearchWriterUtil.tryUpdateByQueryRequest(
      esClient,
      String.format("%d decision definitions", decisionDefIds.size()),
      MARK_AS_DELETED_SCRIPT,
      definitionsToDeleteQuery,
      DECISION_DEFINITION_INDEX_NAME
    );
    if (definitionsUpdated) {
      log.debug("Marked old definitions with new deployments as deleted");
    }
    return definitionsUpdated;
  }

  private void writeDecisionDefinitionInformation(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    String importItemName = "decision definition information";
    log.debug("Writing [{}] {} to ES.", decisionDefinitionOptimizeDtos.size(), importItemName);
    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      decisionDefinitionOptimizeDtos,
      this::addImportDecisionDefinitionXmlRequest
    );
  }

  private void addImportDecisionDefinitionXmlRequest(final BulkRequest bulkRequest,
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
