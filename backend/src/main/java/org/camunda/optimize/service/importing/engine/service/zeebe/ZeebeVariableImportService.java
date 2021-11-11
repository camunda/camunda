/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service.zeebe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableDataDto;
import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;

@Slf4j
public class ZeebeVariableImportService extends ZeebeProcessInstanceSubEntityImportService<ZeebeVariableRecordDto> {

  private final ObjectMapper objectMapper;

  public ZeebeVariableImportService(final ConfigurationService configurationService,
                                    final ZeebeProcessInstanceWriter processInstanceWriter,
                                    final int partitionId,
                                    final ObjectMapper objectMapper,
                                    final ProcessDefinitionReader processDefinitionReader) {
    super(configurationService, processInstanceWriter, partitionId, processDefinitionReader);
    this.objectMapper = objectMapper;
  }

  @Override
  protected List<ProcessInstanceDto> mapZeebeRecordsToOptimizeEntities(
    List<ZeebeVariableRecordDto> zeebeRecords) {
    return zeebeRecords.stream()
      .collect(Collectors.groupingBy(zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
      .values().stream()
      .map(this::createProcessInstanceForData)
      .collect(Collectors.toList());
  }

  private ProcessInstanceDto createProcessInstanceForData(final List<ZeebeVariableRecordDto> recordsForInstance) {
    final ZeebeVariableDataDto firstRecordValue = recordsForInstance.get(0).getValue();
    ProcessDefinitionOptimizeDto processDefinitionOptimizeDto =
      getStoredDefinitionForRecord(firstRecordValue.getProcessDefinitionKey());
    final ProcessInstanceDto instanceToAdd = createSkeletonProcessInstance(
      processDefinitionOptimizeDto.getKey(),
      firstRecordValue.getProcessInstanceKey(),
      firstRecordValue.getProcessDefinitionKey()
    );
    return updateProcessVariables(instanceToAdd, recordsForInstance);
  }

  private ProcessDefinitionOptimizeDto getStoredDefinitionForRecord(final Long definitionKey) {
    return processDefinitionReader.getProcessDefinition(String.valueOf(definitionKey))
      .orElseThrow(() -> new OptimizeRuntimeException(
        "The process definition with id " + definitionKey + " has not yet been imported to Optimize"));
  }

  private ProcessInstanceDto updateProcessVariables(final ProcessInstanceDto instanceToAdd,
                                                    List<ZeebeVariableRecordDto> recordsForInstance) {
    new ArrayList<>(
      recordsForInstance.stream()
        .collect(Collectors.toMap(
          ZeebeVariableRecordDto::getKey,
          Function.identity(),
          (oldVar, newVar) -> (newVar.getPosition() > oldVar.getPosition()) ? newVar : oldVar
        )).values()).forEach(
      variableRecordDto -> convertToSimpleProcessVariableDto(variableRecordDto)
        .ifPresent(variable -> instanceToAdd.getVariables().add(variable)));
    return instanceToAdd;
  }

  private Optional<SimpleProcessVariableDto> convertToSimpleProcessVariableDto(final ZeebeVariableRecordDto variableRecordDto) {
    final ZeebeVariableDataDto zeebeVariableDataDto = variableRecordDto.getValue();
    return getVariableTypeFromJsonNode(zeebeVariableDataDto, variableRecordDto.getKey()).map(type -> {
      SimpleProcessVariableDto simpleProcessVariableDto = new SimpleProcessVariableDto();
      simpleProcessVariableDto.setId(String.valueOf(variableRecordDto.getKey()));
      simpleProcessVariableDto.setName(zeebeVariableDataDto.getName());
      simpleProcessVariableDto.setVersion(variableRecordDto.getPosition());
      simpleProcessVariableDto.setType(type);
      simpleProcessVariableDto.setValue(zeebeVariableDataDto.getValue());
      if (type.equals(STRING_TYPE)) {
        simpleProcessVariableDto.setValue(stripExtraDoubleQuotationsIfExist(zeebeVariableDataDto.getValue()));
      }
      return simpleProcessVariableDto;
    });
  }

  private Optional<String> getVariableTypeFromJsonNode(final ZeebeVariableDataDto zeebeVariableDataDto,
                                                       final long recordKey) {
    try {
      final JsonNode jsonNode = objectMapper.readTree(zeebeVariableDataDto.getValue());
      final JsonNodeType jsonNodeType = jsonNode.getNodeType();
      switch (jsonNodeType) {
        case NUMBER:
          return Optional.of(DOUBLE_TYPE);
        case BOOLEAN:
          return Optional.of(BOOLEAN_TYPE);
        case STRING:
          return Optional.of(STRING_TYPE);
        default:
          return Optional.empty();
      }
    } catch (JsonProcessingException e) {
      log.debug("Could not process json node for variable record with key {}", recordKey);
      return Optional.empty();
    }
  }

  private String stripExtraDoubleQuotationsIfExist(String variableValue) {
    if (variableValue.charAt(0) == '"' && variableValue.charAt(variableValue.length() - 1) == '"') {
      return variableValue.substring(1, variableValue.length() - 1);
    }
    return variableValue;
  }

}
