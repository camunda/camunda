/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
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

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

@Component
public class RunningProcessInstanceWriter {
  private final Logger logger = LoggerFactory.getLogger(RunningProcessInstanceWriter.class);

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  public RunningProcessInstanceWriter(RestHighLevelClient esClient,
                                      ObjectMapper objectMapper, DateTimeFormatter dateTimeFormatter) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  public void importProcessInstances(List<ProcessInstanceDto> processInstances) throws Exception {
    logger.debug("Writing [{}] running process instances to elasticsearch", processInstances.size());

    BulkRequest processInstanceBulkRequest = new BulkRequest();

    for (ProcessInstanceDto procInst : processInstances) {
      addImportProcessInstanceRequest(processInstanceBulkRequest, procInst);
    }
    BulkResponse bulkResponse = esClient.bulk(processInstanceBulkRequest, RequestOptions.DEFAULT);
    if (bulkResponse.hasFailures()) {
      String errorMessage = String.format(
        "There were failures while writing process instance with message: %s",
        bulkResponse.buildFailureMessage()
      );
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  private void addImportProcessInstanceRequest(BulkRequest bulkRequest, ProcessInstanceDto procInst) throws JsonProcessingException {
    String processInstanceId = procInst.getProcessInstanceId();
    Map<String, Object> params = new HashMap<>();
    params.put(ProcessInstanceType.START_DATE, dateTimeFormatter.format(procInst.getStartDate()));
    params.put(ProcessInstanceType.ENGINE, procInst.getEngine());
    params.put(ProcessInstanceType.PROCESS_DEFINITION_VERSION, procInst.getProcessDefinitionVersion());
    params.put(ProcessInstanceType.BUSINESS_KEY, procInst.getBusinessKey());
    params.put(ProcessInstanceType.STATE, procInst.getState());

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.startDate = params.startDate;" +
      "ctx._source.processDefinitionVersion = params.processDefinitionVersion;" +
      "ctx._source.engine = params.engine;" +
      "ctx._source.businessKey = params.businessKey;" +
      "ctx._source.state = params.state",
        params
    );

    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    UpdateRequest request =
      new UpdateRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE), PROC_INSTANCE_TYPE, processInstanceId)
        .script(updateScript)
        .upsert(newEntryIfAbsent, XContentType.JSON);

    bulkRequest.add(request);
  }

}