/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidParameterException;
import java.util.Set;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@AllArgsConstructor
public abstract class AbstractProcessInstanceWriter<T extends OptimizeDto> {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ObjectMapper objectMapper;

  protected void addImportProcessInstanceRequest(BulkRequest bulkRequest,
                                                 T optimizeDto,
                                                 Set<String> primitiveUpdatableFields,
                                                 ObjectMapper objectMapper) {
    if (!(optimizeDto instanceof ProcessInstanceDto)) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    ProcessInstanceDto procInst = (ProcessInstanceDto) optimizeDto;

    final Script updateScript = ElasticsearchWriterUtil.createPrimitiveFieldUpdateScript(
      primitiveUpdatableFields,
      procInst
    );

    String newEntryIfAbsent = "";
    try {
      newEntryIfAbsent = objectMapper.writeValueAsString(procInst);
    } catch (JsonProcessingException e) {
      String reason =
        String.format(
          "Error while processing JSON for process instance DTO with ID [%s].",
          procInst.getProcessInstanceId()
        );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    UpdateRequest request = new UpdateRequest(
      PROCESS_INSTANCE_INDEX_NAME,
      PROCESS_INSTANCE_INDEX_NAME,
      procInst.getProcessInstanceId()
    ).script(updateScript)
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }
}
