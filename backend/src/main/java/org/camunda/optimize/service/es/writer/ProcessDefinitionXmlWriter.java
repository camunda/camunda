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

import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.FLOW_NODE_NAMES;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.USER_TASK_NAMES;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessDefinitionXmlWriter {
  private static final Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(
    FLOW_NODE_NAMES, USER_TASK_NAMES, PROCESS_DEFINITION_XML
  );

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void importProcessDefinitionXmls(List<ProcessDefinitionOptimizeDto> xmls) {
    log.debug("writing [{}] process definition XMLs to ES", xmls.size());
    BulkRequest processDefinitionXmlBulkRequest = new BulkRequest();

    for (ProcessDefinitionOptimizeDto procDefXml : xmls) {
      addImportProcessDefinitionXmlRequest(processDefinitionXmlBulkRequest, procDefXml);
    }

    if (processDefinitionXmlBulkRequest.numberOfActions() > 0) {
      try {
        BulkResponse bulkResponse = esClient.bulk(processDefinitionXmlBulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
          String errorMessage = String.format(
            "There were failures while writing process definition xml information. " +
              "Received error message: %s",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        log.error("There were errors while writing process definition xml information.", e);
      }
    } else {
      log.warn("Cannot import empty list of process definition xmls.");
    }
  }

  private void addImportProcessDefinitionXmlRequest(final BulkRequest bulkRequest,
                                                    final ProcessDefinitionOptimizeDto processDefinitionDto) {
    final Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
      FIELDS_TO_UPDATE,
      processDefinitionDto,
      objectMapper
    );
    final UpdateRequest updateRequest = new UpdateRequest(
      PROCESS_DEFINITION_INDEX_NAME,
      PROCESS_DEFINITION_INDEX_NAME, processDefinitionDto.getId())
      .script(updateScript)
      .upsert(objectMapper.convertValue(processDefinitionDto, Map.class))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }
}
