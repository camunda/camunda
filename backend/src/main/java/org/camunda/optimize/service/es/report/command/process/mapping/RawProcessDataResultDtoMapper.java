/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.process.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.FlowNodeTotalDurationDataDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
public class RawProcessDataResultDtoMapper {

  private static final String DEFAULT_VARIABLE_VALUE = "";
  public static final String OBJECT_VARIABLE_VALUE_PLACEHOLDER = "<<OBJECT_VARIABLE_VALUE>>";

  public List<RawDataProcessInstanceDto> mapFrom(final List<ProcessInstanceDto> processInstanceDtos,
                                                 final ObjectMapper objectMapper,
                                                 final Set<String> allVariableNames,
                                                 final Map<String, Map<String, Integer>> processInstanceIdsToFlowNodeDurations,
                                                 final Map<String, String> flowNodeIdsToFlowNodeNames) {
    final List<RawDataProcessInstanceDto> rawData = new ArrayList<>();
    processInstanceDtos
      .forEach(processInstanceDto -> {
        Map<String, Object> variables = getVariables(processInstanceDto, objectMapper);
        allVariableNames.addAll(variables.keySet());
        RawDataProcessInstanceDto dataEntry = convertToRawDataEntry(
          processInstanceDto,
          variables,
          convertToFlowNodeDurationDataDto(processInstanceIdsToFlowNodeDurations.getOrDefault(
            processInstanceDto.getProcessInstanceId(),
            Collections.emptyMap()
          ),
          flowNodeIdsToFlowNodeNames)
        );
        rawData.add(dataEntry);
      });

    ensureEveryRawDataInstanceContainsAllVariableNames(rawData, allVariableNames);

    return rawData;
  }

  private void ensureEveryRawDataInstanceContainsAllVariableNames(final List<RawDataProcessInstanceDto> rawData,
                                                                  final Set<String> allVariableNames) {
    rawData
      .forEach(data -> allVariableNames
        .forEach(varName -> data.getVariables().putIfAbsent(varName, DEFAULT_VARIABLE_VALUE))
      );
  }

  private RawDataProcessInstanceDto convertToRawDataEntry(final ProcessInstanceDto processInstanceDto,
                                                          final Map<String, Object> variables,
                                                          final Map<String, FlowNodeTotalDurationDataDto> flowNodeIdsToDurations) {
    return new RawDataProcessInstanceDto(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionId(),
      processInstanceDto.getProcessInstanceId(),
      processInstanceDto.getIncidents()
        .stream()
        .filter(incidentDto -> incidentDto.getIncidentStatus() == IncidentStatus.OPEN)
        .count(),
      flowNodeIdsToDurations,
      processInstanceDto.getBusinessKey(),
      processInstanceDto.getStartDate(),
      processInstanceDto.getEndDate(),
      processInstanceDto.getDuration(),
      Optional.ofNullable(processInstanceDto.getDataSource()).map(DataSourceDto::getName).orElse(null),
      processInstanceDto.getTenantId(),
      variables
    );
  }

  private Map<String, Object> getVariables(final ProcessInstanceDto processInstanceDto,
                                           final ObjectMapper objectMapper) {
    Map<String, Object> result = new TreeMap<>();

    for (SimpleProcessVariableDto variableInstance : processInstanceDto.getVariables()) {
      if (variableInstance.getName() != null) {
        if (VariableType.OBJECT.getId().equalsIgnoreCase(variableInstance.getType())) {
          // Object variable value is available on demand in FE so that large values don't distort raw data tables
          result.put(variableInstance.getName(), OBJECT_VARIABLE_VALUE_PLACEHOLDER);
        } else {
          // Convert strings to join list entries for neater display in raw data report UI, or use empty space if null
          result.put(
            variableInstance.getName(),
            Optional.ofNullable(variableInstance.getValue())
              .map(value -> String.join(", ", value))
              .orElse("")
          );
        }
      } else {
        try {
          log.debug("Found variable with null name [{}]", objectMapper.writeValueAsString(variableInstance));
        } catch (JsonProcessingException e) {
          //nothing to do
        }
      }

    }
    return result;
  }

  private Map<String, FlowNodeTotalDurationDataDto> convertToFlowNodeDurationDataDto(final Map<String, Integer> flowNodeIdsToDurations,
                                                                                     final Map<String, String> flowNodeIdsToFlowNodeNames) {
    return flowNodeIdsToDurations.entrySet()
      .stream()
      .collect(Collectors.toMap(Map.Entry::getKey, flowNodeIdToDuration -> new FlowNodeTotalDurationDataDto(
        flowNodeIdsToFlowNodeNames.get(flowNodeIdToDuration.getKey()),
        flowNodeIdToDuration.getValue()
      )));
  }
}
