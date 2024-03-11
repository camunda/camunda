/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;

@Conditional(ElasticSearchCondition.class)
public class AbstractProcessInstanceWriterES
    extends AbstractProcessInstanceDataWriterES<ProcessInstanceDto> {

  protected final ObjectMapper objectMapper;

  protected AbstractProcessInstanceWriterES(
      final OptimizeElasticsearchClient esClient,
      final ElasticSearchSchemaManager elasticSearchSchemaManager,
      final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

  protected void addImportProcessInstanceRequest(
      BulkRequest bulkRequest,
      ProcessInstanceDto processInstanceDto,
      Set<String> updatableFields,
      ObjectMapper objectMapper) {
    final Script updateScript =
        ElasticsearchWriterUtil.createFieldUpdateScript(
            updatableFields, processInstanceDto, objectMapper);
    addImportProcessInstanceRequest(bulkRequest, processInstanceDto, updateScript, objectMapper);
  }

  protected void addImportProcessInstanceRequest(
      BulkRequest bulkRequest,
      ProcessInstanceDto processInstanceDto,
      Script updateScript,
      ObjectMapper objectMapper) {
    final UpdateRequest updateRequest =
        createUpdateRequestDto(processInstanceDto, updateScript, objectMapper);
    bulkRequest.add(updateRequest);
  }

  protected ImportRequestDto createImportRequestForProcessInstance(
      final ProcessInstanceDto processInstanceDto,
      final Set<String> updatableFields,
      final String importItemName) {
    final ScriptData updateScript =
        DatabaseWriterUtil.createScriptData(updatableFields, processInstanceDto, objectMapper);
    return createUpdateRequestDto(processInstanceDto, updateScript, objectMapper, importItemName);
  }

  private UpdateRequest createUpdateRequestDto(
      final ProcessInstanceDto processInstanceDto,
      final Script updateScript,
      final ObjectMapper objectMapper) {
    String newEntryIfAbsent = "";
    try {
      newEntryIfAbsent = objectMapper.writeValueAsString(processInstanceDto);
    } catch (JsonProcessingException e) {
      String reason =
          String.format(
              "Error while processing JSON for process instance DTO with ID [%s].",
              processInstanceDto.getProcessInstanceId());
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

  private ImportRequestDto createUpdateRequestDto(
      final ProcessInstanceDto processInstanceDto,
      final ScriptData updateScriptData,
      final ObjectMapper objectMapper,
      final String importItemName) {
    return ImportRequestDto.builder()
        .indexName(getProcessInstanceIndexAliasName(processInstanceDto.getProcessDefinitionKey()))
        .id(processInstanceDto.getProcessInstanceId())
        .scriptData(updateScriptData)
        .importName(importItemName)
        .source(processInstanceDto)
        .type(RequestType.UPDATE)
        .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
        .build();
  }
}
