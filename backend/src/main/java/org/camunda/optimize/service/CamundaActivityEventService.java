/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.query.event.activity.CamundaActivityEventDto;
import org.camunda.optimize.service.engine.importing.service.ProcessDefinitionResolverService;
import org.camunda.optimize.service.es.writer.CamundaActivityEventWriter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.bpm.engine.ActivityTypes.*;

@AllArgsConstructor
@Component
public class CamundaActivityEventService {

  private static final String START_MAPPED_SUFFIX = "_start";
  private static final String END_MAPPED_SUFFIX = "_end";

  private static final Set<String> SINGLE_MAPPED_TYPES =
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
                    END_EVENT_ERROR, END_EVENT_CANCEL, END_EVENT_TERMINATE, END_EVENT_MESSAGE, END_EVENT_SIGNAL,
                    END_EVENT_COMPENSATION, END_EVENT_ESCALATION, END_EVENT_NONE);

  private static final Set<String> START_END_MAPPED_TYPES =
    Sets.newHashSet(TASK, TASK_SCRIPT, TASK_SERVICE, TASK_BUSINESS_RULE, TASK_MANUAL_TASK,
                    TASK_USER_TASK, TASK_SEND_TASK, TASK_RECEIVE_TASK);

  private final CamundaActivityEventWriter camundaActivityEventWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public void importActivityInstancesToCamundaActivityEvents(List<FlowNodeEventDto> activityInstances) {
    final List<CamundaActivityEventDto> camundaActivityEventDtos = activityInstances
      .stream()
      .flatMap(this::convertFlowNodeToCamundaActivityEvents)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    camundaActivityEventWriter.importActivityInstancesToCamundaActivityEvents(camundaActivityEventDtos);
  }

  private Stream<CamundaActivityEventDto> convertFlowNodeToCamundaActivityEvents(FlowNodeEventDto flowNodeEventDto) {
    if (START_END_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(
        toCamundaActivityEvent(flowNodeEventDto).toBuilder()
          .activityName(flowNodeEventDto.getActivityName() + START_MAPPED_SUFFIX)
          .build(),
        toCamundaActivityEvent(flowNodeEventDto).toBuilder()
          .activityName(flowNodeEventDto.getActivityName() + END_MAPPED_SUFFIX)
          .timestamp(flowNodeEventDto.getEndDate())
          .build()
      );
    } else if (SINGLE_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(toCamundaActivityEvent(flowNodeEventDto).toBuilder().timestamp(flowNodeEventDto.getStartDate()).build());
    }
    return Stream.empty();
  }

  private CamundaActivityEventDto toCamundaActivityEvent(final FlowNodeEventDto flowNodeEventDto) {
    Optional<ProcessDefinitionOptimizeDto> processDefinition =
      processDefinitionResolverService.getDefinitionForProcessDefinitionId(
      flowNodeEventDto.getProcessDefinitionId());
    return CamundaActivityEventDto.builder()
      .activityId(flowNodeEventDto.getActivityId())
      .activityName(flowNodeEventDto.getActivityName())
      .activityType(flowNodeEventDto.getActivityType())
      .processDefinitionKey(flowNodeEventDto.getProcessDefinitionKey())
      .processInstanceId(flowNodeEventDto.getProcessInstanceId())
      .processDefinitionVersion(processDefinition.map(ProcessDefinitionOptimizeDto::getVersion).orElse(null))
      .processDefinitionName(processDefinition.map(ProcessDefinitionOptimizeDto::getName).orElse(null))
      .engine(flowNodeEventDto.getEngineAlias())
      .tenantId(flowNodeEventDto.getTenantId())
      .timestamp(flowNodeEventDto.getStartDate())
      .build();
  }

}
