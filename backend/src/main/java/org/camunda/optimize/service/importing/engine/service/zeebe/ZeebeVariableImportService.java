/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.zeebe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableUpdateDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableDataDto;
import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.OBJECT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;
import static org.camunda.optimize.service.es.schema.index.ExternalProcessVariableIndex.SERIALIZATION_DATA_FORMAT;

@Slf4j
public class ZeebeVariableImportService extends ZeebeProcessInstanceSubEntityImportService<ZeebeVariableRecordDto> {

  private static final Map<String, Object> OBJECT_VALUE_INFO = Map.of(
    SERIALIZATION_DATA_FORMAT, MediaType.APPLICATION_JSON
  );

  private static final Set<VariableIntent> INTENTS_TO_IMPORT = Set.of(
    VariableIntent.CREATED,
    VariableIntent.UPDATED
  );

  private final ObjectMapper objectMapper;
  private final ObjectVariableService objectVariableService;

  public ZeebeVariableImportService(final ConfigurationService configurationService,
                                    final ZeebeProcessInstanceWriter processInstanceWriter,
                                    final int partitionId,
                                    final ObjectMapper objectMapper,
                                    final ProcessDefinitionReader processDefinitionReader,
                                    final ObjectVariableService objectVariableService) {
    super(configurationService, processInstanceWriter, partitionId, processDefinitionReader);
    this.objectMapper = objectMapper;
    this.objectVariableService = objectVariableService;
  }

  @Override
  protected List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
    List<ZeebeVariableRecordDto> zeebeRecords) {
    final List<ProcessInstanceDto> optimizeDtos = zeebeRecords.stream()
      .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
      .collect(Collectors.groupingBy(zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
      .values().stream()
      .map(this::createProcessInstanceForData)
      .collect(toList());
    log.debug(
      "Processing {} fetched zeebe variable records, of which {} are relevant to Optimize and will be imported.",
      zeebeRecords.size(),
      optimizeDtos.size()
    );
    return optimizeDtos;
  }

  private ProcessInstanceDto createProcessInstanceForData(final List<ZeebeVariableRecordDto> recordsForInstance) {
    final ZeebeVariableDataDto firstRecordValue = recordsForInstance.get(0).getValue();
    final ProcessInstanceDto instanceToAdd = createSkeletonProcessInstance(
      getBpmnProcessId(firstRecordValue),
      firstRecordValue.getProcessInstanceKey(),
      firstRecordValue.getProcessDefinitionKey()
    );
    return updateProcessVariables(instanceToAdd, recordsForInstance);
  }

  private String getBpmnProcessId(final ZeebeVariableDataDto zeebeVariableDataDto) {
    return Optional.ofNullable(zeebeVariableDataDto.getBpmnProcessId())
      .orElseGet(() -> {
        // Zeebe variable records older than 1.4.0 didn't contain the process ID, so we have to fetch it from the
        // stored definition. We can remove this handling in future as part of:
        // https://jira.camunda.com/browse/OPT-6065
        final String processDefKey = String.valueOf(zeebeVariableDataDto.getProcessDefinitionKey());
        final Optional<ProcessDefinitionOptimizeDto> processDefinition =
          processDefinitionReader.getProcessDefinition(processDefKey);
        return processDefinition.map(DefinitionOptimizeResponseDto::getKey)
          .orElseThrow(() -> new OptimizeRuntimeException(
            "The process definition with id " + processDefKey + " has not yet been imported to Optimize"));
      });
  }

  private ProcessInstanceDto updateProcessVariables(final ProcessInstanceDto instanceToAdd,
                                                    List<ZeebeVariableRecordDto> recordsForInstance) {
    final List<ProcessVariableUpdateDto> variables = resolveDuplicateUpdates(recordsForInstance)
      .stream()
      .map(this::convertToProcessVariableDto)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(toList());
    final List<ProcessVariableDto> processVariablesToImport;
    if (configurationService.getConfiguredZeebe().isIncludeObjectVariableValue()) {
      processVariablesToImport = objectVariableService.convertToProcessVariableDtos(variables);
    } else {
      processVariablesToImport = objectVariableService.convertToProcessVariableDtosSkippingObjectVariables(variables);
    }
    processVariablesToImport.forEach(variable -> instanceToAdd.getVariables().add(convertToSimpleProcessVariableDto(variable)));
    return instanceToAdd;
  }

  private List<ZeebeVariableRecordDto> resolveDuplicateUpdates(final List<ZeebeVariableRecordDto> recordsForInstance) {
    return new ArrayList<>(
      recordsForInstance.stream()
        .collect(Collectors.toMap(
          ZeebeVariableRecordDto::getKey,
          Function.identity(),
          (oldVar, newVar) -> (newVar.getPosition() > oldVar.getPosition()) ? newVar : oldVar
        )).values());
  }

  private SimpleProcessVariableDto convertToSimpleProcessVariableDto(final ProcessVariableDto processVariableDto) {
    SimpleProcessVariableDto simpleProcessVariableDto = new SimpleProcessVariableDto();
    simpleProcessVariableDto.setId(String.valueOf(processVariableDto.getId()));
    simpleProcessVariableDto.setName(processVariableDto.getName());
    simpleProcessVariableDto.setType(processVariableDto.getType());
    simpleProcessVariableDto.setValue(processVariableDto.getValue());
    simpleProcessVariableDto.setVersion(processVariableDto.getVersion());
    return simpleProcessVariableDto;
  }

  private Optional<ProcessVariableUpdateDto> convertToProcessVariableDto(final ZeebeVariableRecordDto variableRecordDto) {
    final ZeebeVariableDataDto zeebeVariableDataDto = variableRecordDto.getValue();
    return getVariableTypeFromJsonNode(zeebeVariableDataDto, variableRecordDto.getKey()).map(type -> {
      ProcessVariableUpdateDto processVariableDto = new ProcessVariableUpdateDto();
      processVariableDto.setId(String.valueOf(variableRecordDto.getKey()));
      processVariableDto.setName(zeebeVariableDataDto.getName());
      processVariableDto.setVersion(variableRecordDto.getPosition());
      processVariableDto.setType(type);
      processVariableDto.setValue(zeebeVariableDataDto.getValue());
      if (type.equals(STRING_TYPE)) {
        processVariableDto.setValue(stripExtraDoubleQuotationsIfExist(zeebeVariableDataDto.getValue()));
      } else if (OBJECT_TYPE.equalsIgnoreCase(type)) {
        // Zeebe object variables are always in JSON format
        processVariableDto.setValueInfo(OBJECT_VALUE_INFO);
      }
      return processVariableDto;
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
        case OBJECT:
        case ARRAY:
          return Optional.of(OBJECT_TYPE);
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
