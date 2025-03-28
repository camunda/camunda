/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.process.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.FlowNodeTotalDurationDataDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataCountDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataFlowNodeDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RawProcessDataResultDtoMapper {

  private static final String DEFAULT_VARIABLE_VALUE = "";
  public static final String OBJECT_VARIABLE_VALUE_PLACEHOLDER = "<<OBJECT_VARIABLE_VALUE>>";

  public List<RawDataProcessInstanceDto> mapFrom(
      final List<ProcessInstanceDto> processInstanceDtos,
      final ObjectMapper objectMapper,
      final Set<String> allVariableNames,
      final Map<String, Long> instanceIdsToUserTaskCount,
      final Map<String, Map<String, Long>> processInstanceIdsToFlowNodeDurations,
      final Map<String, String> flowNodeIdsToFlowNodeNames) {
    return mapFrom(
        processInstanceDtos,
        objectMapper,
        allVariableNames,
        instanceIdsToUserTaskCount,
        processInstanceIdsToFlowNodeDurations,
        flowNodeIdsToFlowNodeNames,
        true);
  }

  public List<RawDataProcessInstanceDto> mapFrom(
      final List<ProcessInstanceDto> processInstanceDtos,
      final ObjectMapper objectMapper,
      final Set<String> allVariableNames,
      final Map<String, Long> instanceIdsToUserTaskCount,
      final Map<String, Map<String, Long>> processInstanceIdsToFlowNodeDurations,
      final Map<String, String> flowNodeIdsToFlowNodeNames,
      final boolean suppressObjectVariableValues) {
    final List<RawDataProcessInstanceDto> rawData = new ArrayList<>();
    processInstanceDtos.forEach(
        processInstanceDto -> {
<<<<<<< HEAD
          Map<String, Object> variables = getVariables(processInstanceDto, objectMapper);
=======
          final Map<String, Object> variables =
              getVariables(processInstanceDto, objectMapper, suppressObjectVariableValues);
>>>>>>> 3752cb69 (fix: do not suppress object variable values in json export raw data reports)
          allVariableNames.addAll(variables.keySet());
          RawDataProcessInstanceDto dataEntry =
              convertToRawDataEntry(
                  processInstanceDto,
                  variables,
                  instanceIdsToUserTaskCount,
                  convertToFlowNodeDurationDataDto(
                      processInstanceIdsToFlowNodeDurations.getOrDefault(
                          processInstanceDto.getProcessInstanceId(), Collections.emptyMap()),
                      flowNodeIdsToFlowNodeNames),
                  flowNodeIdsToFlowNodeNames);
          rawData.add(dataEntry);
        });

    ensureEveryRawDataInstanceContainsAllVariableNames(rawData, allVariableNames);

    return rawData;
  }

  private void ensureEveryRawDataInstanceContainsAllVariableNames(
      final List<RawDataProcessInstanceDto> rawData, final Set<String> allVariableNames) {
    rawData.forEach(
        data ->
            allVariableNames.forEach(
                varName -> data.getVariables().putIfAbsent(varName, DEFAULT_VARIABLE_VALUE)));
  }

  private RawDataProcessInstanceDto convertToRawDataEntry(
      final ProcessInstanceDto processInstanceDto,
      final Map<String, Object> variables,
      final Map<String, Long> instanceIdsToUserTaskCount,
      final Map<String, FlowNodeTotalDurationDataDto> flowNodeIdsToDurations,
      final Map<String, String> flowNodeIdsToFlowNodeNames) {
    final RawDataCountDto rawDataCountDto = new RawDataCountDto();
    rawDataCountDto.setIncidents(processInstanceDto.getIncidents().size());
    rawDataCountDto.setOpenIncidents(
        processInstanceDto.getIncidents().stream()
            .filter(incidentDto -> incidentDto.getIncidentStatus() == IncidentStatus.OPEN)
            .count());
    rawDataCountDto.setUserTasks(
        instanceIdsToUserTaskCount.getOrDefault(processInstanceDto.getProcessInstanceId(), 0L));
    return new RawDataProcessInstanceDto(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionId(),
        processInstanceDto.getProcessInstanceId(),
        rawDataCountDto,
        flowNodeIdsToDurations,
        processInstanceDto.getBusinessKey(),
        processInstanceDto.getStartDate(),
        processInstanceDto.getEndDate(),
        processInstanceDto.getDuration(),
        Optional.ofNullable(processInstanceDto.getDataSource())
            .map(DataSourceDto::getName)
            .orElse(null),
        processInstanceDto.getTenantId(),
        variables,
        processInstanceDto.getFlowNodeInstances().stream()
            .map(
                flowNodeInstance ->
                    new RawDataFlowNodeDataDto(
                        flowNodeInstance.getFlowNodeInstanceId(),
                        Optional.ofNullable(
                                flowNodeIdsToFlowNodeNames.get(flowNodeInstance.getFlowNodeId()))
                            .orElseGet(flowNodeInstance::getFlowNodeId),
                        flowNodeInstance.getStartDate(),
                        flowNodeInstance.getEndDate()))
            .toList());
  }

  private Map<String, Object> getVariables(
<<<<<<< HEAD
      final ProcessInstanceDto processInstanceDto, final ObjectMapper objectMapper) {
    Map<String, Object> result = new TreeMap<>();
=======
      final ProcessInstanceDto processInstanceDto,
      final ObjectMapper objectMapper,
      final boolean suppressObjectVariableValues) {
    final Map<String, Object> result = new TreeMap<>();
>>>>>>> 3752cb69 (fix: do not suppress object variable values in json export raw data reports)

    for (SimpleProcessVariableDto variableInstance : processInstanceDto.getVariables()) {
      if (variableInstance.getName() != null) {
        if (VariableType.OBJECT.getId().equalsIgnoreCase(variableInstance.getType())) {
          if (suppressObjectVariableValues) {
            // Object variable value is available on demand in FE so that large values don't distort
            // raw data tables
            result.put(variableInstance.getName(), OBJECT_VARIABLE_VALUE_PLACEHOLDER);
          } else {
            result.put(variableInstance.getName(), variableInstance.getValue());
          }
        } else {
          // Convert strings to join list entries for neater display in raw data report UI, or use
          // empty space if null
          result.put(
              variableInstance.getName(),
              Optional.ofNullable(variableInstance.getValue())
                  .map(value -> String.join(", ", value))
                  .orElse(""));
        }
      } else {
        try {
          log.debug(
              "Found variable with null name [{}]",
              objectMapper.writeValueAsString(variableInstance));
        } catch (JsonProcessingException e) {
          // nothing to do
        }
      }
    }
    return result;
  }

  private Map<String, FlowNodeTotalDurationDataDto> convertToFlowNodeDurationDataDto(
      final Map<String, Long> flowNodeIdsToDurations,
      final Map<String, String> flowNodeIdsToFlowNodeNames) {
    return flowNodeIdsToDurations.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                flowNodeIdToDuration ->
                    new FlowNodeTotalDurationDataDto(
                        flowNodeIdsToFlowNodeNames.get(flowNodeIdToDuration.getKey()),
                        ((Number) flowNodeIdToDuration.getValue()).longValue())));
  }
}
