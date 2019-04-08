/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@Component
public class DecisionDefinitionWriter {
  private static final Logger logger = LoggerFactory.getLogger(DecisionDefinitionWriter.class);

  private final ObjectMapper objectMapper;
  private final RestHighLevelClient esClient;

  @Autowired
  public DecisionDefinitionWriter(final RestHighLevelClient esClient,
                                  final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public void importProcessDefinitions(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    logger.debug("Writing [{}] decision definitions to elasticsearch", decisionDefinitionOptimizeDtos.size());
    writeDecisionDefinitionInformation(decisionDefinitionOptimizeDtos);
  }

  private void writeDecisionDefinitionInformation(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    final BulkRequest bulkRequest = new BulkRequest();
    for (DecisionDefinitionOptimizeDto decisionDefinition : decisionDefinitionOptimizeDtos) {
      final String id = decisionDefinition.getId();

      final Map<String, Object> params = new HashMap<>();
      params.put(DecisionDefinitionType.DECISION_DEFINITION_KEY, decisionDefinition.getKey());
      params.put(DecisionDefinitionType.DECISION_DEFINITION_VERSION, decisionDefinition.getVersion());
      params.put(DecisionDefinitionType.DECISION_DEFINITION_NAME, decisionDefinition.getName());
      params.put(DecisionDefinitionType.ENGINE, decisionDefinition.getEngine());

      final Script updateScript = buildUpdateScript(params);

      UpdateRequest request =
        new UpdateRequest(getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE), DECISION_DEFINITION_TYPE, id)
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
            "There were failures while writing decision definition information. Received error message: {}",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        logger.error("There were errors while writing decision definition information.", e);
      }
    } else {
      logger.warn("Cannot import empty list of decision definitions.");
    }
  }

  private Script buildUpdateScript(Map<String, Object> params) {
    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.key = params.key; " +
        "ctx._source.name = params.name; " +
        "ctx._source.engine = params.engine; " +
        "ctx._source.version = params.version; ",
      params
    );
  }

}
