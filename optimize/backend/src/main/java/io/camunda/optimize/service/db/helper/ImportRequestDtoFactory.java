/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.helper;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ImportRequestDtoFactory {

  final ObjectMapper objectMapper;

  public ImportRequestDtoFactory(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ImportRequestDto createImportRequestForProcessInstance(
      final ProcessInstanceDto processInstanceDto,
      final Set<String> updatableFields,
      final String importItemName) {
    final ScriptData updateScript =
        DatabaseWriterUtil.createScriptData(updatableFields, processInstanceDto, objectMapper);
    return createUpsertRequestDtoForProcessInstance(
        processInstanceDto, updateScript, importItemName);
  }

  private ImportRequestDto createUpsertRequestDtoForProcessInstance(
      final ProcessInstanceDto processInstanceDto,
      final ScriptData updateScriptData,
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
