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
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
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

import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION_TAG;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessDefinitionWriter {
  private static final Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(
    PROCESS_DEFINITION_KEY,
    PROCESS_DEFINITION_VERSION,
    PROCESS_DEFINITION_VERSION_TAG,
    PROCESS_DEFINITION_NAME,
    ENGINE,
    TENANT_ID
  );

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void importProcessDefinitions(List<ProcessDefinitionOptimizeDto> procDefs) {
    log.debug("Writing [{}] process definitions to elasticsearch", procDefs.size());
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
              "Received error message: %s",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        log.error("There were errors while writing process definition information.", e);
      }
    } else {
      log.warn("Cannot import empty list of process definitions.");
    }
  }

  private void addUpdateRequestForEachDefinition(List<ProcessDefinitionOptimizeDto> procDefs, BulkRequest bulkRequest) {
    for (ProcessDefinitionOptimizeDto procDef : procDefs) {
      final String id = procDef.getId();
      final Script updateScript = ElasticsearchWriterUtil.createPrimitiveFieldUpdateScript(FIELDS_TO_UPDATE, procDef);
      final UpdateRequest request = new UpdateRequest(PROCESS_DEFINITION_INDEX_NAME, PROCESS_DEFINITION_INDEX_NAME, id)
          .script(updateScript)
          .upsert(objectMapper.convertValue(procDef, Map.class))
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      bulkRequest.add(request);
    }
  }

}
