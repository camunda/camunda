/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
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
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.FLOW_NODE_NAMES;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_DEF_TYPE;

@Component
public class ProcessDefinitionXmlWriter {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionXmlWriter.class);

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;

  @Autowired
  public ProcessDefinitionXmlWriter(RestHighLevelClient esClient,
                                    ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public void importProcessDefinitionXmls(List<ProcessDefinitionOptimizeDto> xmls) {
    logger.debug("writing [{}] process definition XMLs to ES", xmls.size());
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
        logger.error("There were errors while writing process definition xml information.", e);
      }
    } else {
      logger.warn("Cannot import empty list of process definition xmls.");
    }
  }

  private void addImportProcessDefinitionXmlRequest(BulkRequest bulkRequest,
                                                    ProcessDefinitionOptimizeDto newEntryIfAbsent) {

    Map<String, Object> params = new HashMap<>();
    params.put(FLOW_NODE_NAMES, newEntryIfAbsent.getFlowNodeNames());
    params.put(PROCESS_DEFINITION_XML, newEntryIfAbsent.getBpmn20Xml());

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.flowNodeNames = params.flowNodeNames; " +
        "ctx._source.bpmn20Xml = params.bpmn20Xml; ",
      params
    );

    String source = null;
    try {
      source = objectMapper.writeValueAsString(newEntryIfAbsent);
    } catch (JsonProcessingException e) {
      logger.error("can't serialize to JSON", e);
    }

    UpdateRequest updateRequest =
      new UpdateRequest(getOptimizeIndexAliasForType(PROC_DEF_TYPE), PROC_DEF_TYPE, newEntryIfAbsent.getId())
        .script(updateScript)
        .upsert(source, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }
}
