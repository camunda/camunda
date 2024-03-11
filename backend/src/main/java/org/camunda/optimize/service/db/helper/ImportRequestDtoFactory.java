/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.helper;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImportRequestDtoFactory {
  final ObjectMapper objectMapper;

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
