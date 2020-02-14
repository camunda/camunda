/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.Sets;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_CANCEL;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_COMPENSATION;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_CONDITIONAL;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_ERROR;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_ESCALATION;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_MESSAGE;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_SIGNAL;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_TIMER;
import static org.camunda.bpm.engine.ActivityTypes.CALL_ACTIVITY;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_CANCEL;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_COMPENSATION;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_ERROR;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_ESCALATION;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_MESSAGE;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_NONE;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_SIGNAL;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_TERMINATE;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_CATCH;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_COMPENSATION_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_CONDITIONAL;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_ESCALATION_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_LINK;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_MESSAGE;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_MESSAGE_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_NONE_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_SIGNAL;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_SIGNAL_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_TIMER;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_COMPENSATION;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_CONDITIONAL;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_ERROR;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_ESCALATION;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_MESSAGE;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_SIGNAL;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_TIMER;
import static org.camunda.bpm.engine.ActivityTypes.SUB_PROCESS;
import static org.camunda.bpm.engine.ActivityTypes.SUB_PROCESS_AD_HOC;
import static org.camunda.bpm.engine.ActivityTypes.TASK;
import static org.camunda.bpm.engine.ActivityTypes.TASK_BUSINESS_RULE;
import static org.camunda.bpm.engine.ActivityTypes.TASK_MANUAL_TASK;
import static org.camunda.bpm.engine.ActivityTypes.TASK_RECEIVE_TASK;
import static org.camunda.bpm.engine.ActivityTypes.TASK_SCRIPT;
import static org.camunda.bpm.engine.ActivityTypes.TASK_SEND_TASK;
import static org.camunda.bpm.engine.ActivityTypes.TASK_SERVICE;
import static org.camunda.bpm.engine.ActivityTypes.TASK_USER_TASK;
import static org.camunda.bpm.engine.ActivityTypes.TRANSACTION;

@AllArgsConstructor
@Component
@Slf4j
public class CamundaEventImportService {

  public static final String START_MAPPED_SUFFIX = "start";
  public static final String END_MAPPED_SUFFIX = "end";
  public static final String PROCESS_START_TYPE = "processInstanceStart";
  public static final String PROCESS_END_TYPE = "processInstanceEnd";

  private static final Set<String> SINGLE_MAPPED_TYPES =
    // @formatter:off
    Sets.newHashSet(SUB_PROCESS, SUB_PROCESS_AD_HOC, CALL_ACTIVITY, TRANSACTION,
                    ////////////////////////////////////////////////////////////////////////////////////////////
                    BOUNDARY_TIMER, BOUNDARY_MESSAGE, BOUNDARY_SIGNAL, BOUNDARY_COMPENSATION,
                    BOUNDARY_ERROR, BOUNDARY_ESCALATION, BOUNDARY_CANCEL, BOUNDARY_CONDITIONAL,
                    ////////////////////////////////////////////////////////////////////////////////////////////
                    START_EVENT, START_EVENT_TIMER, START_EVENT_MESSAGE, START_EVENT_SIGNAL,
                    START_EVENT_ESCALATION, START_EVENT_COMPENSATION, START_EVENT_ERROR, START_EVENT_CONDITIONAL,
                    ////////////////////////////////////////////////////////////////////////////////////////////
                    INTERMEDIATE_EVENT_CATCH, INTERMEDIATE_EVENT_MESSAGE, INTERMEDIATE_EVENT_TIMER,
                    INTERMEDIATE_EVENT_LINK, INTERMEDIATE_EVENT_SIGNAL, INTERMEDIATE_EVENT_CONDITIONAL,
                    ////////////////////////////////////////////////////////////////////////////////////////////
                    INTERMEDIATE_EVENT_THROW, INTERMEDIATE_EVENT_SIGNAL_THROW, INTERMEDIATE_EVENT_COMPENSATION_THROW,
                    INTERMEDIATE_EVENT_MESSAGE_THROW, INTERMEDIATE_EVENT_NONE_THROW, INTERMEDIATE_EVENT_ESCALATION_THROW,
                    ////////////////////////////////////////////////////////////////////////////////////////////
                    END_EVENT_ERROR, END_EVENT_CANCEL, END_EVENT_TERMINATE, END_EVENT_MESSAGE,
                    END_EVENT_SIGNAL, END_EVENT_COMPENSATION, END_EVENT_ESCALATION, END_EVENT_NONE
    );
  // @formatter:on

  private static final Set<String> START_END_MAPPED_TYPES =
    Sets.newHashSet(TASK, TASK_SCRIPT, TASK_SERVICE, TASK_BUSINESS_RULE, TASK_MANUAL_TASK,
                    TASK_USER_TASK, TASK_SEND_TASK, TASK_RECEIVE_TASK
    );

  private final CamundaActivityEventWriter camundaActivityEventWriter;
  private final VariableUpdateInstanceWriter variableUpdateInstanceWriter;
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
    if (START_END_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(toFlowNodeActivityStartEvent(flowNodeEventDto));
    }
    return Stream.empty();
  }

  private Stream<CamundaActivityEventDto> convertCompletedActivityToCamundaActivityEvents(FlowNodeEventDto flowNodeEventDto) {
    if (START_END_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(
        toFlowNodeActivityStartEvent(flowNodeEventDto),
        toCamundaActivityEvent(flowNodeEventDto).toBuilder()
          .activityId(addDelimiterForStrings(flowNodeEventDto.getActivityId(), END_MAPPED_SUFFIX))
          .activityName(addDelimiterForStrings(flowNodeEventDto.getActivityName(), END_MAPPED_SUFFIX))
          .activityInstanceId(addDelimiterForStrings(flowNodeEventDto.getActivityInstanceId(), END_MAPPED_SUFFIX))
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
      .activityId(addDelimiterForStrings(flowNodeEventDto.getActivityId(), START_MAPPED_SUFFIX))
      .activityName(addDelimiterForStrings(flowNodeEventDto.getActivityName(), START_MAPPED_SUFFIX))
      .activityInstanceId(addDelimiterForStrings(flowNodeEventDto.getActivityInstanceId(), START_MAPPED_SUFFIX))
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

  private Stream<CamundaActivityEventDto> convertRunningProcessInstanceToCamundaActivityEvents(ProcessInstanceDto processInstanceDto) {
    String processDefinitionName = processDefinitionResolverService.getDefinitionForProcessDefinitionId(
      processInstanceDto.getProcessDefinitionId()).map(DefinitionOptimizeDto::getName).orElse(null);
    return Stream.of(toProcessActivity(
      processInstanceDto,
      processDefinitionName,
      PROCESS_START_TYPE,
      processInstanceDto.getStartDate()
    ));
  }

  private Stream<CamundaActivityEventDto> convertCompletedProcessInstanceToCamundaActivityEvents(ProcessInstanceDto processInstanceDto) {
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
