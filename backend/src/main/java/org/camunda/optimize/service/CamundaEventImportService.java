/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.service.es.writer.BusinessKeyWriter;
import org.camunda.optimize.service.es.writer.CamundaActivityEventWriter;
import org.camunda.optimize.service.es.writer.variable.VariableUpdateInstanceWriter;
import org.camunda.optimize.service.importing.engine.service.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.events.CamundaEventService.PROCESS_END_TYPE;
import static org.camunda.optimize.service.events.CamundaEventService.PROCESS_START_TYPE;
import static org.camunda.optimize.service.events.CamundaEventService.SINGLE_MAPPED_TYPES;
import static org.camunda.optimize.service.events.CamundaEventService.SPLIT_START_END_MAPPED_TYPES;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaTaskEndEventSuffix;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaTaskStartEventSuffix;

@AllArgsConstructor
@Component
@Slf4j
public class CamundaEventImportService {



  private final VariableUpdateInstanceWriter variableUpdateInstanceWriter;
  private final CamundaActivityEventWriter camundaActivityEventWriter;
  private final BusinessKeyWriter businessKeyWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ConfigurationService configurationService;

  public void importRunningActivityInstancesToCamundaActivityEvents(List<FlowNodeEventDto> runningActivityInstances) {
    if (configurationService.getEventBasedProcessConfiguration().isEnabled()) {
      importEngineEntityToCamundaActivityEvents(
        runningActivityInstances, this::convertRunningActivityToCamundaActivityEvents
      );
    }
  }

  public void importCompletedActivityInstancesToCamundaActivityEvents(List<FlowNodeEventDto> completedActivityInstances) {
    if (configurationService.getEventBasedProcessConfiguration().isEnabled()) {
      importEngineEntityToCamundaActivityEvents(
        completedActivityInstances, this::convertCompletedActivityToCamundaActivityEvents
      );
    }
  }

  public void importRunningProcessInstances(List<ProcessInstanceDto> runningProcessInstances) {
    if (configurationService.getEventBasedProcessConfiguration().isEnabled()) {
      importEngineEntityToCamundaActivityEvents(
        runningProcessInstances, this::convertRunningProcessInstanceToCamundaActivityEvents
      );
      businessKeyWriter.importBusinessKeysForProcessInstances(runningProcessInstances);
    }
  }

  public void importCompletedProcessInstances(List<ProcessInstanceDto> completedProcessInstances) {
    if (configurationService.getEventBasedProcessConfiguration().isEnabled()) {
      importEngineEntityToCamundaActivityEvents(
        completedProcessInstances, this::convertCompletedProcessInstanceToCamundaActivityEvents
      );
      businessKeyWriter.importBusinessKeysForProcessInstances(completedProcessInstances);
    }
  }

  public void importVariableUpdateInstances(final List<ProcessVariableDto> variableUpdates) {
    if (configurationService.getEventBasedProcessConfiguration().isEnabled()) {
      variableUpdateInstanceWriter.importVariableUpdatesToVariableUpdateInstances(variableUpdates);
    }
  }

  private <T> void importEngineEntityToCamundaActivityEvents(List<T> activitiesToImport,
                                                             Function<T, Stream<CamundaActivityEventDto>> eventExtractor) {

    final List<CamundaActivityEventDto> camundaActivityEventDtos = activitiesToImport
      .stream()
      .flatMap(eventExtractor)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    camundaActivityEventWriter.importCamundaActivityEvents(camundaActivityEventDtos);
  }

  private Stream<CamundaActivityEventDto> convertRunningActivityToCamundaActivityEvents(FlowNodeEventDto flowNodeEventDto) {
    if (SPLIT_START_END_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(toFlowNodeActivityStartEvent(flowNodeEventDto));
    }
    return Stream.empty();
  }

  private Stream<CamundaActivityEventDto> convertCompletedActivityToCamundaActivityEvents(FlowNodeEventDto flowNodeEventDto) {
    if (SPLIT_START_END_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(
        toFlowNodeActivityStartEvent(flowNodeEventDto),
        toCamundaActivityEvent(flowNodeEventDto).toBuilder()
          .activityId(applyCamundaTaskEndEventSuffix(flowNodeEventDto.getActivityId()))
          .activityName(applyCamundaTaskEndEventSuffix(flowNodeEventDto.getActivityName()))
          .activityInstanceId(applyCamundaTaskEndEventSuffix(flowNodeEventDto.getActivityInstanceId()))
          .timestamp(flowNodeEventDto.getEndDate())
          .build()
      );
    } else if (SINGLE_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(toCamundaActivityEvent(flowNodeEventDto).toBuilder()
                         .timestamp(flowNodeEventDto.getStartDate())
                         .build());
    }
    return Stream.empty();
  }

  private CamundaActivityEventDto toFlowNodeActivityStartEvent(final FlowNodeEventDto flowNodeEventDto) {
    return toCamundaActivityEvent(flowNodeEventDto).toBuilder()
      .activityId(applyCamundaTaskStartEventSuffix(flowNodeEventDto.getActivityId()))
      .activityName(applyCamundaTaskStartEventSuffix(flowNodeEventDto.getActivityName()))
      .activityInstanceId(applyCamundaTaskStartEventSuffix(flowNodeEventDto.getActivityInstanceId()))
      .build();
  }

  private CamundaActivityEventDto toCamundaActivityEvent(final FlowNodeEventDto flowNodeEventDto) {
    Optional<ProcessDefinitionOptimizeDto> processDefinition =
      processDefinitionResolverService.getDefinitionForProcessDefinitionId(
        flowNodeEventDto.getProcessDefinitionId());
    return CamundaActivityEventDto.builder()
      .activityId(flowNodeEventDto.getActivityId())
      .activityName(flowNodeEventDto.getActivityName())
      .activityType(flowNodeEventDto.getActivityType())
      .activityInstanceId(flowNodeEventDto.getId())
      .processDefinitionKey(flowNodeEventDto.getProcessDefinitionKey())
      .processInstanceId(flowNodeEventDto.getProcessInstanceId())
      .processDefinitionVersion(processDefinition.map(ProcessDefinitionOptimizeDto::getVersion).orElse(null))
      .processDefinitionName(processDefinition.map(ProcessDefinitionOptimizeDto::getName).orElse(null))
      .engine(flowNodeEventDto.getEngineAlias())
      .tenantId(flowNodeEventDto.getTenantId())
      .timestamp(flowNodeEventDto.getStartDate())
      .build();
  }

  private Stream<CamundaActivityEventDto> convertRunningProcessInstanceToCamundaActivityEvents(
    final ProcessInstanceDto processInstanceDto) {
    String processDefinitionName = processDefinitionResolverService.getDefinitionForProcessDefinitionId(
      processInstanceDto.getProcessDefinitionId()).map(DefinitionOptimizeDto::getName).orElse(null);
    return Stream.of(toProcessActivity(
      processInstanceDto,
      processDefinitionName,
      PROCESS_START_TYPE,
      processInstanceDto.getStartDate()
    ));
  }

  private Stream<CamundaActivityEventDto> convertCompletedProcessInstanceToCamundaActivityEvents(
    final ProcessInstanceDto processInstanceDto) {
    String processDefinitionName = processDefinitionResolverService.getDefinitionForProcessDefinitionId(
      processInstanceDto.getProcessDefinitionId()).map(DefinitionOptimizeDto::getName).orElse(null);
    return Stream.of(
      toProcessActivity(
        processInstanceDto,
        processDefinitionName,
        PROCESS_START_TYPE,
        processInstanceDto.getStartDate()
      ),
      toProcessActivity(processInstanceDto, processDefinitionName, PROCESS_END_TYPE, processInstanceDto.getEndDate())
    );
  }

  private CamundaActivityEventDto toProcessActivity(final ProcessInstanceDto processInstanceDto,
                                                    final String processDefinitionName,
                                                    final String processEventType,
                                                    final OffsetDateTime startDate) {
    return CamundaActivityEventDto.builder()
      .activityId(addDelimiterForStrings(processInstanceDto.getProcessDefinitionKey(), processEventType))
      .activityName(processEventType)
      .activityType(processEventType)
      .activityInstanceId(addDelimiterForStrings(processInstanceDto.getProcessDefinitionKey(), processEventType))
      .processDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .processInstanceId(processInstanceDto.getProcessInstanceId())
      .processDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .processDefinitionName(processDefinitionName)
      .engine(processInstanceDto.getEngine())
      .tenantId(processInstanceDto.getTenantId())
      .timestamp(startDate)
      .build();
  }

  private static String addDelimiterForStrings(String... strings) {
    return String.join("_", strings);
  }

}
