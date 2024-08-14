/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static java.util.stream.Collectors.groupingBy;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Converging;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Diverging;

import io.camunda.optimize.dto.optimize.query.event.autogeneration.AutogenerationAdjacentEventTypesDto;
import io.camunda.optimize.dto.optimize.query.event.autogeneration.AutogenerationEventGraphDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.GatewayDirection;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.EndEventBuilder;
import org.camunda.bpm.model.bpmn.builder.ExclusiveGatewayBuilder;
import org.camunda.bpm.model.bpmn.builder.InclusiveGatewayBuilder;
import org.camunda.bpm.model.bpmn.builder.IntermediateCatchEventBuilder;
import org.camunda.bpm.model.bpmn.builder.ParallelGatewayBuilder;
import org.camunda.bpm.model.bpmn.builder.ProcessBuilder;
import org.camunda.bpm.model.bpmn.builder.StartEventBuilder;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventModelBuilderUtil {

  private static final String EVENT = "event";
  private static final String PROCESS = "process";
  private static final String CONNECTION = "connection";
  private static final String DIVERGING_GATEWAY = "Diverging gateway";
  private static final String CONVERGING_GATEWAY = "Converging gateway";
  private static final int DEFAULT_START_EVENT_HEIGHT = 36;
  private static final int DEFAULT_SPACE_HEIGHT = 50;

  public static AutogenerationEventGraphDto generateExternalEventGraph(
      final List<EventSequenceCountDto> externalEventSequenceCounts) {
    final Map<EventTypeDto, AutogenerationAdjacentEventTypesDto> adjacentEventTypesDtoMap =
        new HashMap<>();
    externalEventSequenceCounts.forEach(
        eventSequenceCountDto -> {
          if (eventSequenceCountDto.getTargetEvent() == null) {
            if (!adjacentEventTypesDtoMap.containsKey(eventSequenceCountDto.getSourceEvent())) {
              adjacentEventTypesDtoMap.put(
                  eventSequenceCountDto.getSourceEvent(),
                  AutogenerationAdjacentEventTypesDto.builder().build());
            }
          } else {
            if (adjacentEventTypesDtoMap.containsKey(eventSequenceCountDto.getSourceEvent())) {
              adjacentEventTypesDtoMap
                  .get(eventSequenceCountDto.getSourceEvent())
                  .getSucceedingEvents()
                  .add(eventSequenceCountDto.getTargetEvent());
            } else {
              final List<EventTypeDto> targetEvents = new ArrayList<>();
              targetEvents.add(eventSequenceCountDto.getTargetEvent());
              adjacentEventTypesDtoMap.put(
                  eventSequenceCountDto.getSourceEvent(),
                  AutogenerationAdjacentEventTypesDto.builder()
                      .succeedingEvents(targetEvents)
                      .build());
            }
            if (adjacentEventTypesDtoMap.containsKey(eventSequenceCountDto.getTargetEvent())) {
              adjacentEventTypesDtoMap
                  .get(eventSequenceCountDto.getTargetEvent())
                  .getPrecedingEvents()
                  .add(eventSequenceCountDto.getSourceEvent());
            } else {
              final List<EventTypeDto> sourceEvents = new ArrayList<>();
              sourceEvents.add(eventSequenceCountDto.getSourceEvent());
              adjacentEventTypesDtoMap.put(
                  eventSequenceCountDto.getTargetEvent(),
                  AutogenerationAdjacentEventTypesDto.builder()
                      .precedingEvents(sourceEvents)
                      .build());
            }
          }
        });

    final List<EventTypeDto> startEvents =
        identifyAndPromoteBestFitStartEvents(adjacentEventTypesDtoMap);
    final List<EventTypeDto> endEvents =
        identifyAndPromoteBestFitEndEvents(adjacentEventTypesDtoMap, startEvents);

    return AutogenerationEventGraphDto.builder()
        .startEvents(startEvents)
        .endEvents(endEvents)
        .adjacentEventTypesDtoMap(adjacentEventTypesDtoMap)
        .build();
  }

  public static String generateNodeId(final EventTypeDto eventTypeDto) {
    return removeIllegalCharacters(generateId(EVENT, eventTypeDto));
  }

  public static String generateGatewayIdForNode(
      final EventTypeDto eventTypeDto, final GatewayDirection gatewayDirection) {
    return removeIllegalCharacters(
        generateId(gatewayDirection.toString().toLowerCase(Locale.ENGLISH), eventTypeDto));
  }

  public static String generateConnectionGatewayIdForDefinitionKey(
      final GatewayDirection direction, final String definitionKey) {
    return String.join(
        "_",
        Arrays.asList(CONNECTION, direction.toString().toLowerCase(Locale.ENGLISH), definitionKey));
  }

  public static String generateTaskIdForDefinitionKey(final String definitionKey) {
    return String.join("_", Arrays.asList(PROCESS, definitionKey));
  }

  public static AbstractFlowNodeBuilder<StartEventBuilder, StartEvent> addStartEvent(
      final EventTypeDto event,
      final String nodeId,
      final ProcessBuilder processBuilder,
      final int startEventIndex) {
    return addStartEvent(getEventName(event), nodeId, processBuilder, startEventIndex);
  }

  public static AbstractFlowNodeBuilder<StartEventBuilder, StartEvent> addStartEvent(
      final String eventName, final String nodeId, final ProcessBuilder processBuilder) {
    return addStartEvent(eventName, nodeId, processBuilder, 0);
  }

  private static AbstractFlowNodeBuilder<StartEventBuilder, StartEvent> addStartEvent(
      final String eventName,
      final String nodeId,
      final ProcessBuilder processBuilder,
      final int startEventIndex) {
    log.debug("Adding start event node with id {} to autogenerated model", nodeId);

    final StartEventBuilder startEventBuilder =
        processBuilder.startEvent(nodeId).message(IdGenerator.getNextId()).name(eventName);
    // We adjust subsequent start event y values as the Model API natively results in cases of
    // overlapping multiple
    // start events. Will be fixed in https://jira.camunda.com/browse/CAM-12012
    final StartEvent startEventAdded = startEventBuilder.done().getModelElementById(nodeId);
    final Bounds startEventBounds = startEventAdded.getDiagramElement().getBounds();
    startEventBounds.setY(
        startEventBounds.getY()
            + (DEFAULT_START_EVENT_HEIGHT * startEventIndex)
            + (DEFAULT_SPACE_HEIGHT * startEventIndex));
    return startEventBuilder;
  }

  public static AbstractFlowNodeBuilder<IntermediateCatchEventBuilder, IntermediateCatchEvent>
      addIntermediateEvent(
          final EventTypeDto event,
          final String nodeId,
          final AbstractFlowNodeBuilder<?, ?> currentBuilder) {
    return addIntermediateEvent(getEventName(event), nodeId, currentBuilder);
  }

  public static AbstractFlowNodeBuilder<IntermediateCatchEventBuilder, IntermediateCatchEvent>
      addIntermediateEvent(
          final String eventName,
          final String nodeId,
          final AbstractFlowNodeBuilder<?, ?> currentBuilder) {
    log.debug("Adding intermediate event node with id {} to autogenerated model", nodeId);
    return currentBuilder
        .intermediateCatchEvent(nodeId)
        .message(IdGenerator.getNextId())
        .name(eventName);
  }

  public static AbstractFlowNodeBuilder<EndEventBuilder, EndEvent> addEndEvent(
      final EventTypeDto event,
      final String nodeId,
      final AbstractFlowNodeBuilder<?, ?> currentBuilder) {
    return addEndEvent(getEventName(event), nodeId, currentBuilder);
  }

  public static AbstractFlowNodeBuilder<EndEventBuilder, EndEvent> addEndEvent(
      final String eventName,
      final String nodeId,
      final AbstractFlowNodeBuilder<?, ?> currentBuilder) {
    log.debug("Adding end event node with id {} to autogenerated model", nodeId);
    return currentBuilder.endEvent(nodeId).message(IdGenerator.getNextId()).name(eventName);
  }

  public static AbstractFlowNodeBuilder<ExclusiveGatewayBuilder, ExclusiveGateway>
      addExclusiveGateway(
          final GatewayDirection gatewayDirection,
          final String nodeId,
          final AbstractFlowNodeBuilder<?, ?> currentBuilder) {
    log.debug(
        "Adding {} parallel gateway with id {} and to model", gatewayDirection.toString(), nodeId);
    return currentBuilder.exclusiveGateway(nodeId).name(getGatewayName(gatewayDirection));
  }

  public static AbstractFlowNodeBuilder<ParallelGatewayBuilder, ParallelGateway> addParallelGateway(
      final GatewayDirection gatewayDirection,
      final String nodeId,
      final AbstractFlowNodeBuilder<?, ?> currentBuilder) {
    log.debug(
        "Adding {} parallel gateway with id {} and to model", gatewayDirection.toString(), nodeId);
    return currentBuilder.parallelGateway(nodeId).name(getGatewayName(gatewayDirection));
  }

  public static AbstractFlowNodeBuilder<InclusiveGatewayBuilder, InclusiveGateway>
      addInclusiveGateway(
          final GatewayDirection gatewayDirection,
          final String nodeId,
          final AbstractFlowNodeBuilder<?, ?> currentBuilder) {
    log.debug(
        "Adding {} inclusive gateway with id {} and to model", gatewayDirection.toString(), nodeId);
    return currentBuilder.inclusiveGateway(nodeId).name(getGatewayName(gatewayDirection));
  }

  public static AbstractFlowNodeBuilder<?, ?> prepareModelBuilderForCurrentSource(
      AbstractFlowNodeBuilder<?, ?> builderToReturn,
      final List<EventTypeDto> startEventsInModel,
      final String definitionKey) {
    if (startEventsInModel.size() > 1) {
      final String connectionGatewayId =
          generateConnectionGatewayIdForDefinitionKey(Diverging, definitionKey);
      builderToReturn = addConnectionGateway(Diverging, connectionGatewayId, builderToReturn);
    }
    return builderToReturn;
  }

  public static AbstractFlowNodeBuilder<?, ?> prepareModelBuilderForNextSource(
      AbstractFlowNodeBuilder<?, ?> builderToReturn,
      final List<EventTypeDto> endEventsInModel,
      final String definitionKey) {
    if (endEventsInModel.size() > 1) {
      final String connectionGatewayId =
          generateConnectionGatewayIdForDefinitionKey(Converging, definitionKey);
      for (final EventTypeDto endEvent : endEventsInModel) {
        builderToReturn = builderToReturn.moveToNode(generateNodeId(endEvent));
        final BpmnModelInstance endEventBuilder = builderToReturn.done();
        if (endEventBuilder.getModelElementById(connectionGatewayId) == null) {
          addConnectionGateway(Converging, connectionGatewayId, builderToReturn);
        } else {
          builderToReturn.connectTo(connectionGatewayId);
        }
      }
      return builderToReturn.moveToNode(connectionGatewayId);
    } else if (endEventsInModel.size() == 1) {
      return builderToReturn.moveToNode(generateNodeId(endEventsInModel.get(0)));
    }
    log.warn("Cannot move builder to end of source model as no end events exist");
    return builderToReturn;
  }

  private static AbstractFlowNodeBuilder<ExclusiveGatewayBuilder, ExclusiveGateway>
      addConnectionGateway(
          final GatewayDirection direction,
          final String gatewayId,
          final AbstractFlowNodeBuilder<?, ?> currentBuilder) {
    log.debug("Adding connecting exclusive gateway with id {} and to model", gatewayId);
    return currentBuilder.exclusiveGateway(gatewayId).name(getGatewayName(direction));
  }

  private static String getEventName(final EventTypeDto event) {
    return Optional.ofNullable(event.getEventLabel()).orElse(event.getEventName());
  }

  private static String getGatewayName(final GatewayDirection gatewayDirection) {
    return Diverging.equals(gatewayDirection) ? DIVERGING_GATEWAY : CONVERGING_GATEWAY;
  }

  private static String generateId(final String type, final EventTypeDto eventTypeDto) {
    // The type prefix is necessary and should start with lower case so that the ID passes QName
    // validation
    return String.join(
        "_",
        Arrays.asList(
            type, eventTypeDto.getGroup(), eventTypeDto.getSource(), eventTypeDto.getEventName()));
  }

  private static List<EventTypeDto> identifyAndPromoteBestFitStartEvents(
      final Map<EventTypeDto, AutogenerationAdjacentEventTypesDto> adjacentEventTypesDtoMap) {
    final Map<Integer, List<Map.Entry<EventTypeDto, AutogenerationAdjacentEventTypesDto>>>
        adjacentEventsByPrecedingEventCount =
            adjacentEventTypesDtoMap.entrySet().stream()
                .collect(groupingBy(entry -> entry.getValue().getPrecedingEvents().size()));
    final List<EventTypeDto> startEvents =
        adjacentEventsByPrecedingEventCount.keySet().stream()
            .min(Integer::compareTo)
            .map(
                minKey ->
                    adjacentEventsByPrecedingEventCount.get(minKey).stream()
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList()))
            .orElse(Collections.emptyList());
    // We make the adjacency Map consistent with the start event selection
    startEvents.forEach(
        startEvent -> {
          adjacentEventTypesDtoMap.get(startEvent).setPrecedingEvents(Collections.emptyList());
          adjacentEventTypesDtoMap.forEach((k, v) -> v.getSucceedingEvents().remove(startEvent));
        });
    return startEvents;
  }

  private static List<EventTypeDto> identifyAndPromoteBestFitEndEvents(
      final Map<EventTypeDto, AutogenerationAdjacentEventTypesDto> adjacentEventTypesDtoMap,
      final List<EventTypeDto> startEvents) {
    final Map<Integer, List<Map.Entry<EventTypeDto, AutogenerationAdjacentEventTypesDto>>>
        adjacentEventsBySucceedingEventCount =
            adjacentEventTypesDtoMap.entrySet().stream()
                .collect(groupingBy(entry -> entry.getValue().getSucceedingEvents().size()));
    final List<EventTypeDto> endEvents =
        adjacentEventsBySucceedingEventCount.keySet().stream()
            .min(Integer::compareTo)
            .map(
                minKey ->
                    adjacentEventsBySucceedingEventCount.get(minKey).stream()
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList()))
            .orElse(Collections.emptyList())
            .stream()
            .filter(endEventCandidate -> !startEvents.contains(endEventCandidate))
            .collect(Collectors.toList());
    // We make the adjacency Map consistent with the end event selection
    endEvents.forEach(
        endEvent -> {
          adjacentEventTypesDtoMap.get(endEvent).setSucceedingEvents(Collections.emptyList());
          adjacentEventTypesDtoMap.forEach((k, v) -> v.getPrecedingEvents().remove(endEvent));
        });
    return endEvents;
  }

  private static String removeIllegalCharacters(final String originalId) {
    return originalId.replaceAll("\\s", "-").replaceAll("[^a-zA-Z0-9_.-]", "-");
  }
}
