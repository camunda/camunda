/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
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

@Component
public class DecisionDefinitionXmlWriter {
  private static final Logger logger = LoggerFactory.getLogger(DecisionDefinitionXmlWriter.class);

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;

  @Autowired
  public DecisionDefinitionXmlWriter(RestHighLevelClient esClient,
                                     ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public void importProcessDefinitionXmls(final List<DecisionDefinitionOptimizeDto> decisionDefinitions) {
    logger.debug("writing [{}] decision definition XMLs to ES", decisionDefinitions.size());

    final BulkRequest processDefinitionXmlBulkRequest = new BulkRequest();
    for (DecisionDefinitionOptimizeDto decisionDefinition : decisionDefinitions) {
      addImportProcessDefinitionXmlRequest(processDefinitionXmlBulkRequest, decisionDefinition);
    }

    if (processDefinitionXmlBulkRequest.numberOfActions() > 0) {
      try {
        BulkResponse bulkResponse = esClient.bulk(processDefinitionXmlBulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
          String errorMessage = String.format(
            "There were failures while writing decision definition xml information. " +
              "Received error message: {}",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        logger.error("There were errors while writing decision definition xml information.", e);
      }
    } else {
      logger.warn("Cannot import empty list of decision definition xmls.");
    }
  }

  private void addImportProcessDefinitionXmlRequest(final BulkRequest bulkRequest,
                                                    final DecisionDefinitionOptimizeDto newEntryIfAbsent) {

    final Map<String, Object> params = new HashMap<>();
    params.put(DecisionDefinitionType.DECISION_DEFINITION_XML, newEntryIfAbsent.getDmn10Xml());

    final Script updateScript = buildUpdateScript(params);

    String source = null;
    try {
      source = objectMapper.writeValueAsString(newEntryIfAbsent);
    } catch (JsonProcessingException e) {
      logger.error("can't serialize to JSON", e);
    }

    UpdateRequest updateRequest =
      new UpdateRequest(
        getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE),
        DECISION_DEFINITION_TYPE,
        newEntryIfAbsent.getId()
      )
        .script(updateScript)
        .upsert(source, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }

  private Script buildUpdateScript(final Map<String, Object> params) {
    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.dmn10Xml = params.dmn10Xml; ",
      params
    );
  }
}
