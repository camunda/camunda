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
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.DECISION_DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.DECISION_DEFINITION_VERSION_TAG;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.ENGINE;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

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
    final BulkRequest bulkRequest = new BulkRequest();
    for (DecisionDefinitionOptimizeDto decisionDefinition : decisionDefinitionOptimizeDtos) {
      final String id = decisionDefinition.getId();

      final Script updateScript = ElasticsearchWriterUtil.createPrimitiveFieldUpdateScript(
        FIELDS_TO_UPDATE,
        decisionDefinition
      );
      final UpdateRequest request = new UpdateRequest(DECISION_DEFINITION_TYPE, DECISION_DEFINITION_TYPE, id)
        .script(updateScript)
        .upsert(objectMapper.convertValue(decisionDefinition, Map.class))
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      bulkRequest.add(request);
    }

    if (bulkRequest.numberOfActions() > 0) {
      final BulkResponse bulkResponse;
      try {
        bulkRequest.setRefreshPolicy(IMMEDIATE);
        bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
          String errorMessage = String.format(
            "There were failures while writing decision definition information. Received error message: %s",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        log.error("There were errors while writing decision definition information.", e);
      }
    } else {
      log.warn("Cannot import empty list of decision definitions.");
    }
  }

}
