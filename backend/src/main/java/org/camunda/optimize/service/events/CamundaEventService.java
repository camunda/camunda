/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.LabeledEventTypeDto;
import org.camunda.optimize.service.ProcessDefinitionService;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
import static org.camunda.optimize.service.util.BpmnModelUtility.parseBpmnModel;

@AllArgsConstructor
@Component
public class CamundaEventService {
  public static final String EVENT_SOURCE_CAMUNDA = "camunda";
  public static final String START_MAPPED_SUFFIX = "start";
  public static final String END_MAPPED_SUFFIX = "end";
  public static final String PROCESS_START_TYPE = "processInstanceStart";
  public static final String PROCESS_END_TYPE = "processInstanceEnd";

  public static final Set<String> START_EVENT_TYPES = ImmutableSet.of(
    START_EVENT, START_EVENT_TIMER, START_EVENT_MESSAGE, START_EVENT_SIGNAL,
    START_EVENT_ESCALATION, START_EVENT_COMPENSATION, START_EVENT_ERROR, START_EVENT_CONDITIONAL
  );

  public static final Set<String> END_EVENT_TYPES = ImmutableSet.of(
    END_EVENT_ERROR, END_EVENT_CANCEL, END_EVENT_TERMINATE, END_EVENT_MESSAGE,
    END_EVENT_SIGNAL, END_EVENT_COMPENSATION, END_EVENT_ESCALATION, END_EVENT_NONE, "endEvent"
  );

  public static final Set<String> START_AND_END_EVENT_TYPES = ImmutableSet.<String>builder()
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

  public static final Set<String> ALL_MAPPED_TYPES = ImmutableSet.<String>builder()
    .addAll(SINGLE_MAPPED_TYPES)
    .addAll(SPLIT_START_END_MAPPED_TYPES)
    .build();

  private final CamundaActivityEventReader camundaActivityEventReader;
  private final ProcessDefinitionService processDefinitionService;

  public List<EventDto> getCamundaActivityEventsForDefinitionAfter(final String definitionKey,
                                                                   final Long eventTimestamp, final int limit) {
    return camundaActivityEventReader.getCamundaActivityEventsForDefinitionAfter(definitionKey, eventTimestamp, limit)
      .stream()
      .map(this::mapToEventDto)
      .collect(Collectors.toList());
  }

  public List<EventDto> getCamundaActivityEventsForDefinitionAt(final String definitionKey, final Long eventTimestamp) {
    return camundaActivityEventReader.getCamundaActivityEventsForDefinitionAt(definitionKey, eventTimestamp)
      .stream()
      .map(this::mapToEventDto)
      .collect(Collectors.toList());
  }

  public List<LabeledEventTypeDto> getLabeledCamundaEventTypesForProcess(final String userId,
                                                                         final String definitionKey,
                                                                         final List<String> versions,
                                                                         final List<String> tenants,
                                                                         final EventScopeType eventScope) {
    List<LabeledEventTypeDto> result;
    switch (eventScope) {
      case ALL:
        result = extractLabeledEventTypeDtos(
          definitionKey, ALL_MAPPED_TYPES, getBpmnModelInstance(userId, definitionKey, versions, tenants)
        );
        result.addAll(createLabeledProcessInstanceStartEndEventTypeDtos(definitionKey));
        break;
      case START_END:
        result = extractLabeledEventTypeDtos(
          definitionKey, START_AND_END_EVENT_TYPES, getBpmnModelInstance(userId, definitionKey, versions, tenants)
        );
        break;
      case PROCESS_INSTANCE:
        result = createLabeledProcessInstanceStartEndEventTypeDtos(definitionKey);
        break;
      default:
        throw new OptimizeValidationException("Unsupported event scope for camunda events : [" + eventScope + "].");
    }
    return result;
  }

  private List<LabeledEventTypeDto> createLabeledProcessInstanceStartEndEventTypeDtos(final String definitionKey) {
    return ImmutableList.of(
      LabeledEventTypeDto.builder()
        .source(EVENT_SOURCE_CAMUNDA)
        .group(definitionKey)
        .eventName(applyCamundaProcessInstanceStartEventSuffix(definitionKey))
        .eventLabel(PROCESS_START_TYPE)
        .build(),
      LabeledEventTypeDto.builder()
        .source(EVENT_SOURCE_CAMUNDA)
        .group(definitionKey)
        .eventName(applyCamundaProcessInstanceEndEventSuffix(definitionKey))
        .eventLabel(PROCESS_END_TYPE)
        .build()
    );
  }

  private List<LabeledEventTypeDto> extractLabeledEventTypeDtos(final String definitionKey,
                                                                final Set<String> typesToInclude,
                                                                final BpmnModelInstance bpmnModel) {
    return bpmnModel.getModel().getTypes().stream()
      .filter(modelElementType -> typesToInclude.contains(modelElementType.getTypeName()))
      .map(bpmnModel::getModelElementsByType)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .distinct()
      .flatMap(modelElementInstance -> {
        final List<LabeledEventTypeDto> eventDtos = new ArrayList<>();
        final String elementId = modelElementInstance.getAttributeValue("id");
        final String elementName = Optional.ofNullable(modelElementInstance.getAttributeValue("name"))
          .orElse(elementId);
        if (SPLIT_START_END_MAPPED_TYPES.contains(modelElementInstance.getElementType().getTypeName())) {
          eventDtos.add(
            LabeledEventTypeDto.builder()
              .source(EVENT_SOURCE_CAMUNDA)
              .group(definitionKey)
              .eventName(applyCamundaTaskStartEventSuffix(elementId))
              .eventLabel(applyCamundaTaskStartEventSuffix(elementName))
              .build()
          );
          eventDtos.add(
            LabeledEventTypeDto.builder()
              .source(EVENT_SOURCE_CAMUNDA)
              .group(definitionKey)
              .eventName(applyCamundaTaskEndEventSuffix(elementId))
              .eventLabel(applyCamundaTaskEndEventSuffix(elementName))
              .build()
          );
        } else {
          eventDtos.add(
            LabeledEventTypeDto.builder()
              .source(EVENT_SOURCE_CAMUNDA)
              .group(definitionKey)
              .eventName(elementId)
              .eventLabel(elementName)
              .build()
          );
        }
        return eventDtos.stream();
      })
      .collect(Collectors.toList());
  }

  private BpmnModelInstance getBpmnModelInstance(final String userId,
                                                 final String definitionKey,
                                                 final List<String> versions,
                                                 final List<String> tenants) {
    final String processDefinitionXml = processDefinitionService
      .getProcessDefinitionXml(userId, definitionKey, versions, tenants)
      .orElseThrow(() -> new OptimizeValidationException(String.format(
        "Could not find process definition for eventSource entry: [key: %s, versions: %s, tenants: %s].",
        definitionKey, versions, tenants
      )));
    return parseBpmnModel(processDefinitionXml);
  }

  private EventDto mapToEventDto(final CamundaActivityEventDto camundaActivityEventDto) {
    return EventDto.builder()
      .id(camundaActivityEventDto.getActivityInstanceId())
      .eventName(camundaActivityEventDto.getActivityId())
      .traceId(camundaActivityEventDto.getProcessInstanceId())
      .timestamp(camundaActivityEventDto.getTimestamp().toInstant().toEpochMilli())
      .ingestionTimestamp(camundaActivityEventDto.getTimestamp().toInstant().toEpochMilli())
      .group(camundaActivityEventDto.getProcessDefinitionKey())
      .source(EVENT_SOURCE_CAMUNDA)
      .build();
  }

  public static String applyCamundaProcessInstanceStartEventSuffix(final String definitionKey) {
    return addDelimiterForStrings(definitionKey, PROCESS_START_TYPE);
  }

  public static String applyCamundaProcessInstanceEndEventSuffix(final String definitionKey) {
    return addDelimiterForStrings(definitionKey, PROCESS_END_TYPE);
  }

  public static String applyCamundaTaskStartEventSuffix(final String identifier) {
    return addDelimiterForStrings(identifier, START_MAPPED_SUFFIX);
  }

  public static String applyCamundaTaskEndEventSuffix(final String identifier) {
    return addDelimiterForStrings(identifier, END_MAPPED_SUFFIX);
  }

  private static String addDelimiterForStrings(final String... strings) {
    return String.join("_", strings);
  }

}
