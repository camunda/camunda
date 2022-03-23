/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.events;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.sequence.OrderedEventDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.util.EventDtoBuilderUtil;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskEndEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskStartEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaEventTypeDto;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaProcessEndEventTypeDto;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaProcessStartEventTypeDto;

@AllArgsConstructor
@Component
public class CamundaEventService {
  public static final String EVENT_SOURCE_CAMUNDA = "camunda";
  public static final String PROCESS_START_TYPE = EventDtoBuilderUtil.PROCESS_START_TYPE;
  public static final String PROCESS_END_TYPE = EventDtoBuilderUtil.PROCESS_END_TYPE;

  private static final Set<String> START_EVENT_TYPES = ImmutableSet.of(
    START_EVENT, START_EVENT_TIMER, START_EVENT_MESSAGE, START_EVENT_SIGNAL,
    START_EVENT_ESCALATION, START_EVENT_COMPENSATION, START_EVENT_ERROR, START_EVENT_CONDITIONAL
  );

  private static final Set<String> END_EVENT_TYPES = ImmutableSet.of(
    END_EVENT_ERROR, END_EVENT_CANCEL, END_EVENT_TERMINATE, END_EVENT_MESSAGE,
    END_EVENT_SIGNAL, END_EVENT_COMPENSATION, END_EVENT_ESCALATION, END_EVENT_NONE, "endEvent"
  );

  private static final Set<String> START_AND_END_EVENT_TYPES = ImmutableSet.<String>builder()
    .addAll(START_EVENT_TYPES)
    .addAll(END_EVENT_TYPES)
    .build();

  public static final Set<String> SINGLE_MAPPED_TYPES =
    ImmutableSet.<String>builder()
      .addAll(START_AND_END_EVENT_TYPES)
      .addAll(
        Sets.newHashSet(
          SUB_PROCESS, SUB_PROCESS_AD_HOC, CALL_ACTIVITY, TRANSACTION,
          ////////////////////////////////////////////////////////////////////////////////////////////
          BOUNDARY_TIMER, BOUNDARY_MESSAGE, BOUNDARY_SIGNAL, BOUNDARY_COMPENSATION,
          BOUNDARY_ERROR, BOUNDARY_ESCALATION, BOUNDARY_CANCEL, BOUNDARY_CONDITIONAL,
          ////////////////////////////////////////////////////////////////////////////////////////////
          INTERMEDIATE_EVENT_CATCH, INTERMEDIATE_EVENT_MESSAGE, INTERMEDIATE_EVENT_TIMER,
          INTERMEDIATE_EVENT_LINK, INTERMEDIATE_EVENT_SIGNAL, INTERMEDIATE_EVENT_CONDITIONAL,
          ////////////////////////////////////////////////////////////////////////////////////////////
          INTERMEDIATE_EVENT_THROW, INTERMEDIATE_EVENT_SIGNAL_THROW, INTERMEDIATE_EVENT_COMPENSATION_THROW,
          INTERMEDIATE_EVENT_MESSAGE_THROW, INTERMEDIATE_EVENT_NONE_THROW, INTERMEDIATE_EVENT_ESCALATION_THROW
        )
      )
      .build();

  public static final Set<String> SPLIT_START_END_MAPPED_TYPES = ImmutableSet.of(
    TASK, TASK_SCRIPT, TASK_SERVICE, TASK_BUSINESS_RULE, TASK_MANUAL_TASK,
    TASK_USER_TASK, TASK_SEND_TASK, TASK_RECEIVE_TASK
  );

  private static final Set<String> ALL_MAPPED_TYPES = ImmutableSet.<String>builder()
    .addAll(SINGLE_MAPPED_TYPES)
    .addAll(SPLIT_START_END_MAPPED_TYPES)
    .build();

  private final CamundaActivityEventReader camundaActivityEventReader;
  private final DefinitionService definitionService;

  public List<OrderedEventDto> getTraceableCamundaEventsForDefinitionAfter(final String definitionKey,
                                                                           final Long eventTimestamp, final int limit) {
    return camundaActivityEventReader.getCamundaActivityEventsForDefinitionAfter(definitionKey, eventTimestamp, limit)
      .stream()
      .filter(this::isStateTraceable)
      .map(this::mapToEventDto)
      .collect(Collectors.toList());
  }

  public List<OrderedEventDto> getTraceableCamundaEventsForDefinitionAt(final String definitionKey,
                                                                        final Long eventTimestamp) {
    return camundaActivityEventReader.getCamundaActivityEventsForDefinitionAt(definitionKey, eventTimestamp)
      .stream()
      .filter(this::isStateTraceable)
      .map(this::mapToEventDto)
      .collect(Collectors.toList());
  }

  public List<EventTypeDto> getLabeledCamundaEventTypesForProcess(final String userId,
                                                                  final CamundaEventSourceEntryDto camundaEventSourceEntryDto) {
    List<EventTypeDto> result = new ArrayList<>();
    if (camundaEventSourceEntryDto.getConfiguration().getEventScope().contains(EventScopeType.ALL)) {
      result.addAll(extractEventTypesFromProcessDefinitionDto(
        camundaEventSourceEntryDto.getConfiguration().getProcessDefinitionKey(),
        ALL_MAPPED_TYPES,
        getProcessDefinition(userId, camundaEventSourceEntryDto)
      ));
    } else if (camundaEventSourceEntryDto.getConfiguration().getEventScope().contains(EventScopeType.START_END)) {
      result.addAll(extractEventTypesFromProcessDefinitionDto(
        camundaEventSourceEntryDto.getConfiguration().getProcessDefinitionKey(),
        START_AND_END_EVENT_TYPES,
        getProcessDefinition(userId, camundaEventSourceEntryDto)
      ));
    }
    if (camundaEventSourceEntryDto.getConfiguration().getEventScope().contains(EventScopeType.PROCESS_INSTANCE)) {
      result.addAll(createLabeledProcessInstanceStartEndEventTypeDtos(
        camundaEventSourceEntryDto.getConfiguration().getProcessDefinitionKey()));
    }
    return result;
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestampsForDefinition(final String processDefinitionKey) {
    return camundaActivityEventReader.getMinAndMaxIngestedTimestampsForDefinition(processDefinitionKey);
  }

  private boolean isStateTraceable(CamundaActivityEventDto camundaActivityEventDto) {
    return !camundaActivityEventDto.getActivityType().equalsIgnoreCase(PROCESS_START_TYPE) &&
      !camundaActivityEventDto.getActivityType().equalsIgnoreCase(PROCESS_END_TYPE);
  }

  private List<EventTypeDto> createLabeledProcessInstanceStartEndEventTypeDtos(final String definitionKey) {
    return ImmutableList.of(
      createCamundaProcessStartEventTypeDto(definitionKey),
      createCamundaProcessEndEventTypeDto(definitionKey)
    );
  }

  private List<EventTypeDto> extractEventTypesFromProcessDefinitionDto(final String definitionKey,
                                                                       final Set<String> typesToInclude,
                                                                       final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto) {

    return processDefinitionOptimizeDto.getFlowNodeData()
      .stream()
      .filter(flowNode -> typesToInclude.contains(flowNode.getType()))
      .flatMap(flowNode -> {
        final List<EventTypeDto> eventDtos = new ArrayList<>();
        final String elementId = flowNode.getId();
        final String elementName = Optional.ofNullable(flowNode.getName()).orElse(elementId);
        if (SPLIT_START_END_MAPPED_TYPES.contains(flowNode.getType())) {
          eventDtos.add(
            createCamundaEventTypeDto(
              definitionKey,
              applyCamundaTaskStartEventSuffix(elementId),
              applyCamundaTaskStartEventSuffix(elementName)
            )
          );
          eventDtos.add(
            createCamundaEventTypeDto(
              definitionKey,
              applyCamundaTaskEndEventSuffix(elementId),
              applyCamundaTaskEndEventSuffix(elementName)
            )
          );
        } else {
          eventDtos.add(createCamundaEventTypeDto(definitionKey, elementId, elementName));
        }
        return eventDtos.stream();
      })
      .collect(Collectors.toList());
  }

  private ProcessDefinitionOptimizeDto getProcessDefinition(final String userId,
                                                            final CamundaEventSourceEntryDto camundaEventSourceEntryDto) {
    final CamundaEventSourceConfigDto eventSourceConfig = camundaEventSourceEntryDto.getConfiguration();
    final DefinitionOptimizeResponseDto processDefinitionWithXml = definitionService
      .getDefinitionWithXml(DefinitionType.PROCESS, userId, eventSourceConfig.getProcessDefinitionKey(),
                            eventSourceConfig.getVersions(), eventSourceConfig.getTenants()
      )
      .orElseThrow(() -> new OptimizeValidationException(String.format(
        "Could not find process definition for eventSource entry: [key: %s, versions: %s, tenants: %s].",
        eventSourceConfig.getProcessDefinitionKey(), eventSourceConfig.getVersions(), eventSourceConfig.getTenants()
      )));
    return (ProcessDefinitionOptimizeDto) processDefinitionWithXml;
  }

  private OrderedEventDto mapToEventDto(final CamundaActivityEventDto camundaActivityEventDto) {
    return OrderedEventDto.builder()
      .id(camundaActivityEventDto.getActivityInstanceId())
      .eventName(camundaActivityEventDto.getActivityId())
      .traceId(camundaActivityEventDto.getProcessInstanceId())
      .timestamp(camundaActivityEventDto.getTimestamp().toInstant().toEpochMilli())
      .ingestionTimestamp(camundaActivityEventDto.getTimestamp().toInstant().toEpochMilli())
      .group(camundaActivityEventDto.getProcessDefinitionKey())
      .source(EVENT_SOURCE_CAMUNDA)
      .orderCounter(camundaActivityEventDto.getOrderCounter())
      .build();
  }

}
