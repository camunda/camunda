/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static io.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.OBJECT_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_VARIABLE_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.ExternalProcessVariableIndex.SERIALIZATION_DATA_FORMAT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableUpdateDto;
import io.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableDataDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.http.MediaType;

public class ZeebeVariableImportService
    extends ZeebeProcessInstanceSubEntityImportService<ZeebeVariableRecordDto> {

  private static final Map<String, Object> OBJECT_VALUE_INFO =
      Map.of(SERIALIZATION_DATA_FORMAT, MediaType.APPLICATION_JSON);

  private static final Set<VariableIntent> INTENTS_TO_IMPORT =
      Set.of(VariableIntent.CREATED, VariableIntent.UPDATED);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeVariableImportService.class);

  private final ObjectMapper objectMapper;
  private final ObjectVariableService objectVariableService;

  public ZeebeVariableImportService(
      final ConfigurationService configurationService,
      final ProcessInstanceWriter processInstanceWriter,
      final int partitionId,
      final ObjectMapper objectMapper,
      final ProcessDefinitionReader processDefinitionReader,
      final ObjectVariableService objectVariableService,
      final DatabaseClient databaseClient) {
    super(
        configurationService,
        processInstanceWriter,
        partitionId,
        processDefinitionReader,
        databaseClient,
        ZEEBE_VARIABLE_INDEX_NAME);
    this.objectMapper = objectMapper;
    this.objectVariableService = objectVariableService;
  }

  @Override
  protected List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
      final List<ZeebeVariableRecordDto> zeebeRecords) {
    final List<ProcessInstanceDto> optimizeDtos =
        zeebeRecords.stream()
            .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
            .collect(
                Collectors.groupingBy(
                    zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
            .values()
            .stream()
            .map(this::createProcessInstanceForData)
            .toList();
    LOG.debug(
        "Processing {} fetched zeebe variable records, of which {} are relevant to Optimize and will be imported.",
        zeebeRecords.size(),
        optimizeDtos.size());
    return optimizeDtos;
  }

  private ProcessInstanceDto createProcessInstanceForData(
      final List<ZeebeVariableRecordDto> recordsForInstance) {
    final ZeebeVariableDataDto firstRecordValue = recordsForInstance.get(0).getValue();
    final ProcessInstanceDto instanceToAdd =
        createSkeletonProcessInstance(
            getBpmnProcessId(firstRecordValue),
            firstRecordValue.getProcessInstanceKey(),
            firstRecordValue.getProcessDefinitionKey(),
            firstRecordValue.getTenantId());
    return updateProcessVariables(instanceToAdd, recordsForInstance);
  }

  private String getBpmnProcessId(final ZeebeVariableDataDto zeebeVariableDataDto) {
    return Optional.ofNullable(zeebeVariableDataDto.getBpmnProcessId())
        .orElseGet(
            () -> {
              // Zeebe variable records older than 1.4.0 didn't contain the process ID, so we have
              // to fetch it from the
              // stored definition. We can remove this handling in future as part of:
              // https://jira.camunda.com/browse/OPT-6065
              final String processDefKey =
                  String.valueOf(zeebeVariableDataDto.getProcessDefinitionKey());
              final Optional<ProcessDefinitionOptimizeDto> processDefinition =
                  processDefinitionReader.getProcessDefinition(processDefKey);
              return processDefinition
                  .map(DefinitionOptimizeResponseDto::getKey)
                  .orElseThrow(
                      () ->
                          new OptimizeRuntimeException(
                              "The process definition with id "
                                  + processDefKey
                                  + " has not yet been imported to Optimize"));
            });
  }

  private ProcessInstanceDto updateProcessVariables(
      final ProcessInstanceDto instanceToAdd,
      final List<ZeebeVariableRecordDto> recordsForInstance) {
    final List<ProcessVariableUpdateDto> variables =
        resolveDuplicateUpdates(recordsForInstance).stream()
            .map(this::convertToProcessVariableDto)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    final List<ProcessVariableDto> processVariablesToImport;
    if (configurationService.getConfiguredZeebe().isIncludeObjectVariableValue()) {
      processVariablesToImport = objectVariableService.convertToProcessVariableDtos(variables);
    } else {
      processVariablesToImport =
          objectVariableService.convertToProcessVariableDtosSkippingObjectVariables(variables);
    }
    processVariablesToImport.forEach(
        variable -> instanceToAdd.getVariables().add(convertToSimpleProcessVariableDto(variable)));
    return instanceToAdd;
  }

  private List<ZeebeVariableRecordDto> resolveDuplicateUpdates(
      final List<ZeebeVariableRecordDto> recordsForInstance) {
    return new ArrayList<>(
        recordsForInstance.stream()
            .collect(
                Collectors.toMap(
                    ZeebeVariableRecordDto::getKey,
                    Function.identity(),
                    (oldVar, newVar) ->
                        (newVar.getPosition() > oldVar.getPosition()) ? newVar : oldVar))
            .values());
  }

  private SimpleProcessVariableDto convertToSimpleProcessVariableDto(
      final ProcessVariableDto processVariableDto) {
    final SimpleProcessVariableDto simpleProcessVariableDto = new SimpleProcessVariableDto();
    simpleProcessVariableDto.setId(String.valueOf(processVariableDto.getId()));
    simpleProcessVariableDto.setName(processVariableDto.getName());
    simpleProcessVariableDto.setType(processVariableDto.getType());
    simpleProcessVariableDto.setValue(processVariableDto.getValue());
    simpleProcessVariableDto.setVersion(processVariableDto.getVersion());
    return simpleProcessVariableDto;
  }

  private Optional<ProcessVariableUpdateDto> convertToProcessVariableDto(
      final ZeebeVariableRecordDto variableRecordDto) {
    final ZeebeVariableDataDto zeebeVariableDataDto = variableRecordDto.getValue();
    return getVariableTypeFromJsonNode(zeebeVariableDataDto, variableRecordDto.getKey())
        .map(
            type -> {
              final ProcessVariableUpdateDto processVariableDto = new ProcessVariableUpdateDto();
              processVariableDto.setId(String.valueOf(variableRecordDto.getKey()));
              processVariableDto.setName(zeebeVariableDataDto.getName());
              processVariableDto.setVersion(variableRecordDto.getPosition());
              processVariableDto.setType(type);
              processVariableDto.setValue(zeebeVariableDataDto.getValue());
              processVariableDto.setTenantId(zeebeVariableDataDto.getTenantId());
              if (type.equals(STRING_TYPE)) {
                processVariableDto.setValue(
                    stripExtraDoubleQuotationsIfExist(zeebeVariableDataDto.getValue()));
              } else if (OBJECT_TYPE.equalsIgnoreCase(type)) {
                // Zeebe object variables are always in JSON format
                processVariableDto.setValueInfo(OBJECT_VALUE_INFO);
              }
              return processVariableDto;
            });
  }

  private Optional<String> getVariableTypeFromJsonNode(
      final ZeebeVariableDataDto zeebeVariableDataDto, final long recordKey) {
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
        case OBJECT:
        case ARRAY:
          return Optional.of(OBJECT_TYPE);
        default:
          return Optional.empty();
      }
    } catch (final JsonProcessingException e) {
      LOG.debug("Could not process json node for variable record with key {}", recordKey);
      return Optional.empty();
    }
  }

  private String stripExtraDoubleQuotationsIfExist(final String variableValue) {
    if (variableValue.charAt(0) == '"' && variableValue.charAt(variableValue.length() - 1) == '"') {
      return variableValue.substring(1, variableValue.length() - 1);
    }
    return variableValue;
  }
}
