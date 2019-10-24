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
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION_TAG;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

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

  public void importProcessDefinitions(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    log.debug("Writing [{}] decision definitions to elasticsearch", decisionDefinitionOptimizeDtos.size());
    writeDecisionDefinitionInformation(decisionDefinitionOptimizeDtos);
  }

  private void writeDecisionDefinitionInformation(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    String importItemName = "decision definition information";
    log.debug("Writing [{}] {} to ES.", decisionDefinitionOptimizeDtos.size(), importItemName);
    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      decisionDefinitionOptimizeDtos,
      (request, dto) -> addImportDecisionDefinitionXmlRequest(request, dto)
    );
  }

  private void addImportDecisionDefinitionXmlRequest(final BulkRequest bulkRequest,
                                                     final OptimizeDto optimizeDto) {
    if (!(optimizeDto instanceof DecisionDefinitionOptimizeDto)) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    DecisionDefinitionOptimizeDto decisionDefinitionDto = (DecisionDefinitionOptimizeDto) optimizeDto;

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
