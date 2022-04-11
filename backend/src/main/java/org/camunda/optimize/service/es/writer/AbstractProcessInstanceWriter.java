/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.XContentType;

import java.util.Set;

import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

public abstract class AbstractProcessInstanceWriter extends AbstractProcessInstanceDataWriter<ProcessInstanceDto> {
  protected final ObjectMapper objectMapper;

  protected AbstractProcessInstanceWriter(final OptimizeElasticsearchClient esClient,
                                          final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                          final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

  protected void addImportProcessInstanceRequest(BulkRequest bulkRequest,
                                                 ProcessInstanceDto processInstanceDto,
                                                 Set<String> updatableFields,
                                                 ObjectMapper objectMapper) {
    final Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
      updatableFields,
      processInstanceDto,
      objectMapper
    );
    addImportProcessInstanceRequest(bulkRequest, processInstanceDto, updateScript, objectMapper);
  }

  protected void addImportProcessInstanceRequest(BulkRequest bulkRequest,
                                                 ProcessInstanceDto processInstanceDto,
                                                 Script updateScript,
                                                 ObjectMapper objectMapper) {
    final UpdateRequest updateRequest = createUpdateRequest(processInstanceDto, updateScript, objectMapper);
    bulkRequest.add(updateRequest);
  }

  protected UpdateRequest createImportRequestForProcessInstance(final ProcessInstanceDto processInstanceDto,
                                                                final Set<String> updatableFields) {
    final Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
      updatableFields,
      processInstanceDto,
      objectMapper
    );
    return createUpdateRequest(processInstanceDto, updateScript, objectMapper);
  }

  private UpdateRequest createUpdateRequest(final ProcessInstanceDto processInstanceDto,
                                            final Script updateScript,
                                            final ObjectMapper objectMapper) {
    String newEntryIfAbsent = "";
    try {
      newEntryIfAbsent = objectMapper.writeValueAsString(processInstanceDto);
    } catch (JsonProcessingException e) {
      String reason =
        String.format(
          "Error while processing JSON for process instance DTO with ID [%s].",
          processInstanceDto.getProcessInstanceId()
        );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return new UpdateRequest()
      .index(getProcessInstanceIndexAliasName(processInstanceDto.getProcessDefinitionKey()))
      .id(processInstanceDto.getProcessInstanceId())
      .script(updateScript)
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
  }

}
