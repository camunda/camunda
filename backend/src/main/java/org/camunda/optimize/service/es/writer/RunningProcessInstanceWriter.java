/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class RunningProcessInstanceWriter {
  private static final Set<String> PRIMITIVE_UPDATABLE_FIELDS = ImmutableSet.of(
    PROCESS_DEFINITION_KEY, PROCESS_DEFINITION_VERSION, PROCESS_DEFINITION_ID,
    BUSINESS_KEY, START_DATE, STATE,
    ENGINE, TENANT_ID
  );

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void importProcessInstances(List<ProcessInstanceDto> processInstances) throws Exception {
    log.debug("Writing [{}] running process instances to elasticsearch", processInstances.size());

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

  private void addImportProcessInstanceRequest(BulkRequest bulkRequest, ProcessInstanceDto procInst)
    throws JsonProcessingException {
    final String processInstanceId = procInst.getProcessInstanceId();

    final Script updateScript = ElasticsearchWriterUtil.createPrimitiveFieldUpdateScript(
      PRIMITIVE_UPDATABLE_FIELDS,
      procInst
    );

    final String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    final UpdateRequest request = new UpdateRequest(
      PROCESS_INSTANCE_INDEX_NAME,
      PROCESS_INSTANCE_INDEX_NAME, processInstanceId)
      .script(updateScript)
      .upsert(newEntryIfAbsent, XContentType.JSON);

    bulkRequest.add(request);
  }

}