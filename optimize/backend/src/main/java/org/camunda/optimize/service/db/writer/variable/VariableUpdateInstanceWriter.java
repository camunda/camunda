/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.variable;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.db.DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.service.db.repository.VariableRepository;
import org.camunda.optimize.service.util.IdGenerator;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class VariableUpdateInstanceWriter {
  private final VariableRepository variableRepository;

  public List<ImportRequestDto> generateVariableUpdateImports(
      final List<ProcessVariableDto> variableUpdates) {
    final List<VariableUpdateInstanceDto> variableUpdateInstances =
        variableUpdates.stream().map(this::mapToVariableUpdateInstance).toList();

    String importItemName = "variable instances";
    log.debug("Creating imports for {} [{}].", variableUpdates.size(), importItemName);

    return variableUpdateInstances.stream()
        .map(
            variableUpdateInstanceDto ->
                createIndexRequestForVariableUpdate(variableUpdateInstanceDto, importItemName))
        .toList();
  }

  public void deleteByProcessInstanceIds(final List<String> processInstanceIds) {
    log.info("Deleting variable updates for [{}] processInstanceIds", processInstanceIds.size());
    variableRepository.deleteByProcessInstanceIds(processInstanceIds);
  }

  private VariableUpdateInstanceDto mapToVariableUpdateInstance(
      final ProcessVariableDto processVariable) {
    return VariableUpdateInstanceDto.builder()
        .instanceId(processVariable.getId())
        .name(processVariable.getName())
        .type(processVariable.getType())
        .value(
            processVariable.getValue() == null
                ? Collections.emptyList()
                : processVariable.getValue().stream().filter(Objects::nonNull).collect(toList()))
        .processInstanceId(processVariable.getProcessInstanceId())
        .tenantId(processVariable.getTenantId())
        .timestamp(processVariable.getTimestamp())
        .build();
  }

  private ImportRequestDto createIndexRequestForVariableUpdate(
      VariableUpdateInstanceDto variableUpdateInstanceDto, final String importItemName) {
    return ImportRequestDto.builder()
        .indexName(VARIABLE_UPDATE_INSTANCE_INDEX_NAME)
        .id(IdGenerator.getNextId())
        .source(variableUpdateInstanceDto)
        .type(RequestType.INDEX)
        .importName(importItemName)
        .build();
  }
}
