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
import org.camunda.optimize.dto.optimize.query.event.EventCountServiceDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountSuggestionsRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.service.es.reader.EventReader;
import org.camunda.optimize.service.es.reader.EventSequenceCountReader;
import org.camunda.optimize.service.es.writer.EventWriter;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.engine.importing.BpmnModelUtility.extractFlowNodeNames;
import static org.camunda.optimize.service.engine.importing.BpmnModelUtility.parseBpmnModel;

@AllArgsConstructor
@Component
public class EventService {

  private final EventReader eventReader;
  private final EventWriter eventWriter;
  private final EventSequenceCountReader eventSequenceCountReader;

  private static final Comparator SUGGESTED_COMPARATOR = Comparator.comparing(EventCountDto::isSuggested).reversed();
  private static final Comparator DEFAULT_COMPARATOR =
    Comparator.comparing(EventCountDto::getGroup, String.CASE_INSENSITIVE_ORDER)
      .thenComparing(EventCountDto::getSource, String.CASE_INSENSITIVE_ORDER)
      .thenComparing(EventCountDto::getEventName, String.CASE_INSENSITIVE_ORDER);

  public void saveEvent(final EventDto eventDto) {
    eventWriter.upsertEvent(eventDto);
  }

  public void saveEventBatch(final List<EventDto> eventDtos) {
    eventWriter.upsertEvents(eventDtos);
  }

  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    return eventReader.getEventsIngestedAfter(ingestTimestamp, limit);
  }

  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    return eventReader.getEventsIngestedAt(ingestTimestamp);
  }

  public Long countEventsIngestedBeforeAndAtIngestTimestamp(final Long ingestTimestamp) {
    return eventReader.countEventsIngestedBeforeAndAtIngestTimestamp(ingestTimestamp);
  }

  public List<EventCountDto> getEventCounts(EventCountServiceDto eventCountServiceDto) {
    List<EventCountDto> eventCountDtos = eventReader.getEventCounts(eventCountServiceDto.getEventCountRequestDto());
    applySuggestionsToEventCounts(eventCountServiceDto, eventCountDtos);
    return sortEventCountsUsingWithRequestParameters(eventCountServiceDto.getEventCountRequestDto(), eventCountDtos);
  }

  private void applySuggestionsToEventCounts(final EventCountServiceDto eventCountServiceDto,
                                             final List<EventCountDto> eventCountDtos) {
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto =
      eventCountServiceDto.getEventCountSuggestionsRequestDto();
    if (eventCountSuggestionsRequestDto != null && eventCountSuggestionsRequestDto.getMappings() != null) {
      addSuggestionsForEventCounts(eventCountSuggestionsRequestDto, eventCountDtos);
    }
  }

  private void addSuggestionsForEventCounts(EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto,
                                            List<EventCountDto> eventCountDtos) {
    Map<String, EventMappingDto> currentMappings = eventCountSuggestionsRequestDto.getMappings();
    final BpmnModelInstance bpmnModelForXml = parseBpmnModel(eventCountSuggestionsRequestDto.getXml());

    validateEventCountSuggestionsParameters(eventCountSuggestionsRequestDto, currentMappings, bpmnModelForXml);

    List<EventSequenceCountDto> eventSequenceCountDtos = eventSequenceCountReader.getAllEventSequenceCounts();

    FlowNode targetFlowNode = bpmnModelForXml.getModelElementById(eventCountSuggestionsRequestDto.getTargetFlowNodeId());
    final List<EventTypeDto> suggestedEvents = new ArrayList<>();
    suggestedEvents.addAll(getEventsOccurringBeforeNextMapping(targetFlowNode, currentMappings, eventSequenceCountDtos));
    suggestedEvents.addAll(getEventsOccurringAfterPreviousMapping(targetFlowNode, currentMappings, eventSequenceCountDtos));

    removeAlreadyMappedEventsFromCounts(eventCountDtos, currentMappings);
    eventCountDtos.forEach(eventCountDto -> {
      if (eventCountIsInEventTypeList(eventCountDto, suggestedEvents)) {
        eventCountDto.setSuggested(true);
      }
    });
  }

  private void validateEventCountSuggestionsParameters(final EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto,
                                                       final Map<String, EventMappingDto> currentMappings,
                                                       final BpmnModelInstance bpmnModelForXml) {
    Map<String, String> xmlFlowNodeIds = eventCountSuggestionsRequestDto.getXml() == null ? Collections.emptyMap() :
      extractFlowNodeNames(bpmnModelForXml);
    String targetFlowNodeId = eventCountSuggestionsRequestDto.getTargetFlowNodeId();
    if (targetFlowNodeId != null && !xmlFlowNodeIds.containsKey(targetFlowNodeId)) {
      throw new BadRequestException("Target Flow Node IDs must exist within the provided XML");
    }
    if (currentMappings != null && !xmlFlowNodeIds.keySet().containsAll(currentMappings.keySet())) {
      throw new BadRequestException("All Flow Node IDs for event mappings must exist within the provided XML");
    }
  }

  private List<EventTypeDto> getEventsOccurringBeforeNextMapping(FlowNode targetFlowNode,
                                                                 Map<String, EventMappingDto> currentMappings,
                                                                 List<EventSequenceCountDto> eventSequenceCountDtos) {
    List<EventTypeDto> eventsMappedToOutgoingNodes = getNearestOutgoingMappedFlowNodeIds(currentMappings, targetFlowNode)
      .stream()
      .map(Objects.requireNonNull(currentMappings)::get)
      .map(mapping -> Optional.ofNullable(mapping.getStart()).orElse(mapping.getEnd()))
      .collect(Collectors.toList());
    return eventSequenceCountDtos.stream()
      .filter(sequence -> eventsMappedToOutgoingNodes.contains(sequence.getTargetEvent()))
      .map(EventSequenceCountDto::getSourceEvent)
      .collect(Collectors.toList());
  }

  private List<EventTypeDto> getEventsOccurringAfterPreviousMapping(FlowNode targetFlowNode,
                                                                    Map<String, EventMappingDto> currentMappings,
                                                                    List<EventSequenceCountDto> eventSequenceCountDtos) {
    List<EventTypeDto> eventsMappedToIncomingNodes = getNearestIncomingMappedFlowNodeIds(currentMappings, targetFlowNode)
      .stream()
      .map(Objects.requireNonNull(currentMappings)::get)
      .map(mapping -> Optional.ofNullable(mapping.getEnd()).orElse(mapping.getStart()))
      .collect(Collectors.toList());
    return eventSequenceCountDtos.stream()
      .filter(sequence -> eventsMappedToIncomingNodes.contains(sequence.getSourceEvent()))
      .map(EventSequenceCountDto::getTargetEvent)
      .collect(Collectors.toList());
  }

  private boolean eventCountIsInEventTypeList(EventCountDto eventCountDto, List<EventTypeDto> eventTypes) {
    return eventTypes.stream()
      .anyMatch(suggested -> Objects.equals(suggested.getGroup(), eventCountDto.getGroup())
        && Objects.equals(suggested.getSource(), eventCountDto.getSource())
        && Objects.equals(suggested.getEventName(), eventCountDto.getEventName()));
  }

  private List<String> getNearestIncomingMappedFlowNodeIds(final Map<String, EventMappingDto> currentMappings,
                                                           final FlowNode targetFlowNode) {
    return getNearestMappedFlowNodeIds(currentMappings, targetFlowNode, target ->
      target.getIncoming().stream().map(SequenceFlow::getSource).collect(Collectors.toList()));
  }

  private List<String> getNearestOutgoingMappedFlowNodeIds(final Map<String, EventMappingDto> currentMappings,
                                                           final FlowNode targetFlowNode) {
    return getNearestMappedFlowNodeIds(currentMappings, targetFlowNode, target ->
      target.getOutgoing().stream().map(SequenceFlow::getTarget).collect(Collectors.toList()));
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

  private void removeAlreadyMappedEventsFromCounts(final List<EventCountDto> eventCountDtos,
                                                   final Map<String, EventMappingDto> currentMappings) {
    final List<EventTypeDto> currentMappedEvents = currentMappings.keySet()
      .stream()
      .flatMap(key -> Stream.of(currentMappings.get(key).getStart(), currentMappings.get(key).getEnd()))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    eventCountDtos.removeIf(eventCountDto -> eventCountIsInEventTypeList(eventCountDto, currentMappedEvents));
  }

  private List<EventCountDto> sortEventCountsUsingWithRequestParameters(final EventCountRequestDto eventCountRequestDto,
                                                                        List<EventCountDto> eventCountDtos) {
    SortOrder sortOrder = eventCountRequestDto.getSortOrder();
    boolean isAscending = sortOrder == null || sortOrder.equals(SortOrder.ASC);
    Comparator secondaryComparator = Optional.ofNullable(eventCountRequestDto.getOrderBy())
      .map(orderBy -> sortOrderedComparator(isAscending, getCustomComparator(orderBy))
        .thenComparing(sortOrderedComparator(isAscending, DEFAULT_COMPARATOR)))
      .orElseGet(() -> sortOrderedComparator(isAscending, DEFAULT_COMPARATOR));
    eventCountDtos.sort(SUGGESTED_COMPARATOR.thenComparing(secondaryComparator));
    return eventCountDtos;
  }

  private Comparator sortOrderedComparator(final boolean isAscending, final Comparator comparator) {
    return isAscending ? comparator : comparator.reversed();
  }

  private Comparator getCustomComparator(final String orderBy) {
    if (orderBy.equalsIgnoreCase(EventCountDto.Fields.group)) {
      return Comparator.comparing(EventCountDto::getGroup, String.CASE_INSENSITIVE_ORDER);
    } else if (orderBy.equalsIgnoreCase(EventCountDto.Fields.source)) {
      return Comparator.comparing(EventCountDto::getSource, String.CASE_INSENSITIVE_ORDER);
    } else if (orderBy.equalsIgnoreCase(EventCountDto.Fields.eventName)) {
      return Comparator.comparing(EventCountDto::getEventName, String.CASE_INSENSITIVE_ORDER);
    } else {
      throw new OptimizeValidationException("invalid orderBy field");
    }
  }

}
