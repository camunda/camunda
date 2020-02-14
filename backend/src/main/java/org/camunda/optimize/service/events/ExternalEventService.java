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
import org.camunda.optimize.service.es.reader.ExternalEventReader;
import org.camunda.optimize.service.es.reader.ExternalEventSequenceCountReader;
import org.camunda.optimize.service.es.writer.ExternalEventWriter;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static org.camunda.optimize.service.util.BpmnModelUtility.extractFlowNodeNames;
import static org.camunda.optimize.service.util.BpmnModelUtility.parseBpmnModel;

@AllArgsConstructor
@Component
public class ExternalEventService implements EventFetcherService {

  private final ExternalEventReader eventReader;
  private final ExternalEventWriter eventWriter;
  private final ExternalEventSequenceCountReader eventSequenceCountReader;

  private static final Comparator<EventCountDto> SUGGESTED_COMPARATOR =
    Comparator.comparing(EventCountDto::isSuggested, nullsFirst(naturalOrder())).reversed();
  private static final Comparator<EventCountDto> DEFAULT_COMPARATOR = nullsFirst(
    Comparator.comparing(EventCountDto::getGroup, nullsFirst(String.CASE_INSENSITIVE_ORDER))
      .thenComparing(EventCountDto::getSource, nullsFirst(String.CASE_INSENSITIVE_ORDER))
      .thenComparing(EventCountDto::getEventName, nullsFirst(String.CASE_INSENSITIVE_ORDER)));

  public void saveEventBatch(final List<EventDto> eventDtos) {
    eventWriter.upsertEvents(eventDtos);
  }

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    return eventReader.getEventsIngestedAfter(ingestTimestamp, limit);
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    return eventReader.getEventsIngestedAt(ingestTimestamp);
  }

  public Long countEventsIngestedBeforeAndAtIngestTimestamp(final Long ingestTimestamp) {
    return eventReader.countEventsIngestedBeforeAndAtIngestTimestamp(ingestTimestamp);
  }

  public List<EventCountDto> getEventCounts(EventCountServiceDto eventCountServiceDto) {
    List<EventCountDto> matchingEventCountDtos =
      eventSequenceCountReader.getEventCounts(eventCountServiceDto.getEventCountRequestDto());
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto =
      eventCountServiceDto.getEventCountSuggestionsRequestDto();
    if (eventCountSuggestionsRequestDto != null && eventCountSuggestionsRequestDto.getMappings() != null) {
      removePreviouslyMappedNonTargetEventsFromCounts(matchingEventCountDtos, eventCountSuggestionsRequestDto);
      addSuggestionsForEventCounts(eventCountSuggestionsRequestDto, matchingEventCountDtos);
    }
    return sortEventCountsUsingWithRequestParameters(
      eventCountServiceDto.getEventCountRequestDto(),
      matchingEventCountDtos
    );
  }

  private void addSuggestionsForEventCounts(EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto,
                                            List<EventCountDto> eventCountDtos) {
    Map<String, EventMappingDto> currentMappings = eventCountSuggestionsRequestDto.getMappings();
    final BpmnModelInstance bpmnModelForXml = parseBpmnModel(eventCountSuggestionsRequestDto.getXml());

    validateEventCountSuggestionsParameters(eventCountSuggestionsRequestDto, currentMappings, bpmnModelForXml);

    FlowNode targetFlowNode =
      bpmnModelForXml.getModelElementById(eventCountSuggestionsRequestDto.getTargetFlowNodeId());
    List<EventTypeDto> eventsMappedToIncomingNodes = getNearestIncomingMappedEvents(currentMappings, targetFlowNode);
    List<EventTypeDto> eventsMappedToOutgoingNodes = getNearestOutgoingMappedEvents(currentMappings, targetFlowNode);

    final List<EventTypeDto> suggestedEvents = getSuggestedEventsForGivenMappings(
      eventsMappedToIncomingNodes,
      eventsMappedToOutgoingNodes
    );

    eventCountDtos.forEach(eventCountDto -> {
      if (eventCountIsInEventTypeList(eventCountDto, suggestedEvents)) {
        eventCountDto.setSuggested(true);
      }
    });
  }

  private List<EventTypeDto> getSuggestedEventsForGivenMappings(final List<EventTypeDto> eventsMappedToIncomingNodes,
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
      .collect(Collectors.toList());
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

  private boolean eventCountIsInEventTypeList(EventCountDto eventCountDto, List<EventTypeDto> eventTypes) {
    return eventTypes.stream()
      .anyMatch(suggested ->
                  (Objects.equals(suggested.getGroup(), eventCountDto.getGroup())
                    && Objects.equals(suggested.getSource(), eventCountDto.getSource())
                    && Objects.equals(suggested.getEventName(), eventCountDto.getEventName())));
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

  private void removePreviouslyMappedNonTargetEventsFromCounts(final List<EventCountDto> eventCountDtos,
                                                               final EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto) {
    Map<String, EventMappingDto> currentMappings = eventCountSuggestionsRequestDto.getMappings();
    final List<EventTypeDto> currentMappedNonTargetEvents = currentMappings.keySet()
      .stream()
      .filter(mappedEvent -> !mappedEvent.equals(eventCountSuggestionsRequestDto.getTargetFlowNodeId()))
      .flatMap(key -> Stream.of(currentMappings.get(key).getStart(), currentMappings.get(key).getEnd()))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    eventCountDtos.removeIf(eventCountDto -> eventCountIsInEventTypeList(eventCountDto, currentMappedNonTargetEvents));
  }

  private List<EventCountDto> sortEventCountsUsingWithRequestParameters(final EventCountRequestDto eventCountRequestDto,
                                                                        List<EventCountDto> eventCountDtos) {
    SortOrder sortOrder = eventCountRequestDto.getSortOrder();
    boolean isAscending = sortOrder == null || sortOrder.equals(SortOrder.ASC);
    Comparator<EventCountDto> secondaryComparator = Optional.ofNullable(eventCountRequestDto.getOrderBy())
      .map(orderBy -> sortOrderedComparator(isAscending, getCustomComparator(orderBy))
        .thenComparing(sortOrderedComparator(isAscending, DEFAULT_COMPARATOR)))
      .orElseGet(() -> sortOrderedComparator(isAscending, DEFAULT_COMPARATOR));
    eventCountDtos.sort(SUGGESTED_COMPARATOR.thenComparing(secondaryComparator));
    return eventCountDtos;
  }

  private Comparator<EventCountDto> sortOrderedComparator(final boolean isAscending,
                                                          final Comparator<EventCountDto> comparator) {
    return isAscending ? comparator : comparator.reversed();
  }

  private Comparator<EventCountDto> getCustomComparator(final String orderBy) {
    if (orderBy.equalsIgnoreCase(EventCountDto.Fields.group)) {
      return Comparator.comparing(EventCountDto::getGroup, nullsFirst(String.CASE_INSENSITIVE_ORDER));
    } else if (orderBy.equalsIgnoreCase(EventCountDto.Fields.source)) {
      return Comparator.comparing(EventCountDto::getSource, nullsFirst(String.CASE_INSENSITIVE_ORDER));
    } else if (orderBy.equalsIgnoreCase(EventCountDto.Fields.eventName)) {
      return Comparator.comparing(EventCountDto::getEventName, nullsFirst(String.CASE_INSENSITIVE_ORDER));
    } else {
      throw new OptimizeValidationException("invalid orderBy field");
    }
  }

}
