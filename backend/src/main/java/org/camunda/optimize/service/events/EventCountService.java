/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.optimize.dto.optimize.query.event.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.service.es.reader.ExternalEventSequenceCountReader;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.util.BpmnModelUtility.extractFlowNodeNames;
import static org.camunda.optimize.service.util.BpmnModelUtility.parseBpmnModel;

@AllArgsConstructor
@Component
public class EventCountService {

  private final ExternalEventSequenceCountReader eventSequenceCountReader;
  private final CamundaEventService camundaEventService;

  public List<EventCountDto> getEventCounts(final String userId,
                                            final String searchTerm,
                                            final EventCountRequestDto eventCountRequestDto) {
    if (eventCountRequestDto == null) {
      return Collections.emptyList();
    }

    final List<EventCountDto> matchingEventCountDtos = eventCountRequestDto
      .getEventSources()
      .stream()
      .map(eventSourceEntryDto -> {
        if (eventSourceEntryDto.getType().equals(EventSourceType.EXTERNAL)) {
          return eventSequenceCountReader.getEventCounts(searchTerm);
        } else {
          return getEventCountsForCamundaProcess(
            userId,
            searchTerm,
            eventSourceEntryDto.getProcessDefinitionKey(),
            eventSourceEntryDto.getVersions(),
            eventSourceEntryDto.getTenants(),
            eventSourceEntryDto.getEventScope()
          );
        }
      })
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

    if (eventCountRequestDto.getEventSources().size() == 1
      && eventCountRequestDto.getEventSources().get(0).getType().equals(EventSourceType.EXTERNAL)) {
      addSuggestionsForExternalEventCounts(eventCountRequestDto, matchingEventCountDtos);
    }

    return matchingEventCountDtos;
  }

  private List<EventCountDto> getEventCountsForCamundaProcess(final String userId,
                                                              final String searchTerm,
                                                              final String definitionKey,
                                                              final List<String> versions,
                                                              final List<String> tenants,
                                                              final EventScopeType eventScope) {
    return camundaEventService
      .getLabeledCamundaEventTypesForProcess(userId, definitionKey, versions, tenants, eventScope)
      .stream()
      .filter(eventDto -> searchTerm == null
        || eventDto.getEventName().contains(searchTerm)
        || eventDto.getEventLabel().contains(searchTerm)
        || eventDto.getGroup().contains(searchTerm)
        || eventDto.getSource().contains(searchTerm))
      .map(labeledEventTypeDto ->
             EventCountDto.builder()
               .source(labeledEventTypeDto.getSource())
               .group(labeledEventTypeDto.getGroup())
               .eventName(labeledEventTypeDto.getEventName())
               .eventLabel(labeledEventTypeDto.getEventLabel())
               .build()
      )
      .collect(Collectors.toList());
  }

  private void addSuggestionsForExternalEventCounts(final EventCountRequestDto eventCountRequestDto,
                                                    final List<EventCountDto> eventCountDtos) {
    if (eventCountRequestDto.getXml() == null
      || eventCountRequestDto.getTargetFlowNodeId() == null
      || eventCountRequestDto.getMappings() == null
      || eventCountRequestDto.getMappings().isEmpty()) {
      return;
    }

    final Map<String, EventMappingDto> currentMappings = eventCountRequestDto.getMappings();
    final BpmnModelInstance bpmnModelForXml = parseBpmnModel(eventCountRequestDto.getXml());

    validateEventCountSuggestionsParameters(eventCountRequestDto, currentMappings, bpmnModelForXml);

    final FlowNode targetFlowNode = bpmnModelForXml.getModelElementById(eventCountRequestDto.getTargetFlowNodeId());
    final Set<EventTypeDto> suggestedEvents = getSuggestedExternalEventsForGivenMappings(
      getNearestIncomingMappedEvents(currentMappings, targetFlowNode),
      getNearestOutgoingMappedEvents(currentMappings, targetFlowNode)
    );
    final Set<EventTypeDto> alreadyMappedAndNotTargetNodeEvents = currentMappings.keySet().stream()
      .filter(mappedEvent -> !mappedEvent.equals(eventCountRequestDto.getTargetFlowNodeId()))
      .flatMap(key -> Stream.of(currentMappings.get(key).getStart(), currentMappings.get(key).getEnd()))
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
    suggestedEvents.removeAll(alreadyMappedAndNotTargetNodeEvents);

    eventCountDtos
      .stream()
      .filter(eventCountDto -> eventCountIsPresentInEventTypes(eventCountDto, suggestedEvents))
      .forEach(eventCountDto -> eventCountDto.setSuggested(true));
  }

  private Set<EventTypeDto> getSuggestedExternalEventsForGivenMappings(final List<EventTypeDto> eventsMappedToIncomingNodes,
                                                                       final List<EventTypeDto> eventsMappedToOutgoingNodes) {
    List<EventSequenceCountDto> suggestedEventSequenceCandidates =
      eventSequenceCountReader.getEventSequencesWithSourceInIncomingOrTargetInOutgoing(
        eventsMappedToIncomingNodes, eventsMappedToOutgoingNodes
      );

    return suggestedEventSequenceCandidates.stream()
      .filter(sequence -> Objects.nonNull(sequence.getTargetEvent()))
      .map(suggestionSequence -> {
        if (eventsMappedToIncomingNodes.contains(suggestionSequence.getSourceEvent())) {
          return suggestionSequence.getTargetEvent();
        } else if (eventsMappedToOutgoingNodes.contains(suggestionSequence.getTargetEvent())) {
          return suggestionSequence.getSourceEvent();
        } else {
          return null;
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  private void validateEventCountSuggestionsParameters(final EventCountRequestDto eventCountRequestDto,
                                                       final Map<String, EventMappingDto> currentMappings,
                                                       final BpmnModelInstance bpmnModelForXml) {
    Map<String, String> xmlFlowNodeIds = eventCountRequestDto.getXml() == null ? Collections.emptyMap() :
      extractFlowNodeNames(bpmnModelForXml);
    String targetFlowNodeId = eventCountRequestDto.getTargetFlowNodeId();
    if (targetFlowNodeId != null && !xmlFlowNodeIds.containsKey(targetFlowNodeId)) {
      throw new BadRequestException("Target Flow Node IDs must exist within the provided XML");
    }
    if (currentMappings != null && !xmlFlowNodeIds.keySet().containsAll(currentMappings.keySet())) {
      throw new BadRequestException("All Flow Node IDs for event mappings must exist within the provided XML");
    }
  }

  private boolean eventCountIsPresentInEventTypes(final EventCountDto eventCountDto,
                                                  final Set<EventTypeDto> eventTypes) {
    return eventTypes.contains(
      EventTypeDto.builder()
        .eventName(eventCountDto.getEventName())
        .group(eventCountDto.getGroup())
        .source(eventCountDto.getSource())
        .build()
    );
  }

  private List<EventTypeDto> getNearestIncomingMappedEvents(final Map<String, EventMappingDto> currentMappings,
                                                            final FlowNode targetFlowNode) {
    return getNearestMappedFlowNodeIds(currentMappings, targetFlowNode, target ->
      target.getIncoming().stream().map(SequenceFlow::getSource).collect(Collectors.toList()))
      .stream()
      .map(Objects.requireNonNull(currentMappings)::get)
      .map(mapping -> Optional.ofNullable(mapping.getEnd()).orElse(mapping.getStart()))
      .collect(Collectors.toList());
  }

  private List<EventTypeDto> getNearestOutgoingMappedEvents(final Map<String, EventMappingDto> currentMappings,
                                                            final FlowNode targetFlowNode) {
    return getNearestMappedFlowNodeIds(currentMappings, targetFlowNode, target ->
      target.getOutgoing().stream().map(SequenceFlow::getTarget).collect(Collectors.toList()))
      .stream()
      .map(Objects.requireNonNull(currentMappings)::get)
      .map(mapping -> Optional.ofNullable(mapping.getStart()).orElse(mapping.getEnd()))
      .collect(Collectors.toList());
  }

  /**
   * Mapped Ids at a distance of 1 are first considered. If none are found, we look at a distance of 2. All flow nodes
   * are considered, regardless of whether or not events can be mapped to the element type
   */
  private List<String> getNearestMappedFlowNodeIds(final Map<String, EventMappingDto> currentMappings,
                                                   final FlowNode targetFlowNode,
                                                   final Function<FlowNode, List<FlowNode>> getNextFlowNodeCandidates) {
    List<FlowNode> nearestFlowNodes = getNextFlowNodeCandidates.apply(targetFlowNode);
    List<String> mappedFlowNodeIds = findMappedFlowNodeIdsFromList(currentMappings, nearestFlowNodes);
    if (mappedFlowNodeIds.isEmpty()) {
      final List<FlowNode> flowNodeStream = nearestFlowNodes.stream()
        .flatMap(flowNode -> getNextFlowNodeCandidates.apply(flowNode).stream()).collect(Collectors.toList());
      mappedFlowNodeIds = findMappedFlowNodeIdsFromList(currentMappings, flowNodeStream);
    }
    return mappedFlowNodeIds;
  }

  private List<String> findMappedFlowNodeIdsFromList(final Map<String, EventMappingDto> currentMappings,
                                                     final List<FlowNode> flowNodes) {
    return flowNodes.stream()
      .filter(node -> Objects.requireNonNull(currentMappings).containsKey(node.getId()))
      .map(FlowNode::getId)
      .collect(Collectors.toList());
  }

}
