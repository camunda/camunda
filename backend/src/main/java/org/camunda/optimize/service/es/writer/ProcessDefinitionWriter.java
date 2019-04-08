/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
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
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

@Component
public class ProcessDefinitionWriter {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionWriter.class);

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;

  @Autowired
  public ProcessDefinitionWriter(RestHighLevelClient esClient,
                                 ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public void importProcessDefinitions(List<ProcessDefinitionOptimizeDto> procDefs) {
    logger.debug("Writing [{}] process definitions to elasticsearch", procDefs.size());
    writeProcessDefinitionInformation(procDefs);
  }

  private void writeProcessDefinitionInformation(List<ProcessDefinitionOptimizeDto> procDefs) {
    BulkRequest bulkRequest = new BulkRequest();
    addUpdateRequestForEachDefinition(procDefs, bulkRequest);

    if (bulkRequest.numberOfActions() > 0) {
      final BulkResponse bulkResponse;
      try {
        bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
          String errorMessage = String.format(
            "There were failures while writing process definition information. " +
              "Received error message: {}",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        logger.error("There were errors while writing process definition information.", e);
      }
    } else {
      logger.warn("Cannot import empty list of process definitions.");
    }
  }

  private void addUpdateRequestForEachDefinition(List<ProcessDefinitionOptimizeDto> procDefs, BulkRequest bulkRequest) {
    for (ProcessDefinitionOptimizeDto procDef : procDefs) {
      String id = procDef.getId();

      Map<String, Object> params = new HashMap<>();
      params.put(ProcessDefinitionType.PROCESS_DEFINITION_KEY, procDef.getKey());
      params.put(ProcessDefinitionType.PROCESS_DEFINITION_VERSION, procDef.getVersion());
      params.put(ProcessDefinitionType.PROCESS_DEFINITION_NAME, procDef.getName());
      params.put(ProcessDefinitionType.ENGINE, procDef.getEngine());

      Script updateScript = new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.key = params.key; " +
          "ctx._source.name = params.name; " +
          "ctx._source.engine = params.engine; " +
          "ctx._source.version = params.version; ",
        params
      );

      UpdateRequest request =
        new UpdateRequest(
          getOptimizeIndexAliasForType(ElasticsearchConstants.PROC_DEF_TYPE),
          ElasticsearchConstants.PROC_DEF_TYPE,
          id
        )
          .script(updateScript)
          .upsert(objectMapper.convertValue(procDef, Map.class))
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      bulkRequest.add(request);
    }
  }

}
