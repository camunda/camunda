/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.events;

import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;
import static io.camunda.optimize.service.util.BpmnModelUtil.extractFlowNodeData;
import static io.camunda.optimize.service.util.EventDtoBuilderUtil.fromEventCountDto;

import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import io.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import io.camunda.optimize.service.EventProcessService;
import io.camunda.optimize.service.db.events.EventSequenceCountReaderFactory;
import io.camunda.optimize.service.db.reader.EventSequenceCountReader;
import io.camunda.optimize.service.util.BpmnModelUtil;
import jakarta.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.ModelParseException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventCountService {

  private final EventSequenceCountReader eventSequenceCountReader;
  private final EventProcessService eventProcessService;

  public EventCountService(
      final EventProcessService eventProcessService,
      final EventSequenceCountReaderFactory eventSequenceCountReaderFactory) {
    this.eventProcessService = eventProcessService;
    eventSequenceCountReader =
        eventSequenceCountReaderFactory.createEventSequenceCountReader(
            EXTERNAL_EVENTS_INDEX_SUFFIX);
  }

  public List<EventCountResponseDto> getEventCounts(
      final String userId,
      final String searchTerm,
      final EventCountRequestDto eventCountRequestDto) {
    if (eventCountRequestDto == null || eventCountRequestDto.getEventSources().isEmpty()) {
      return Collections.emptyList();
    }
    eventProcessService.validateEventSources(userId, eventCountRequestDto.getEventSources());

    final Set<String> sequenceCountIndexSuffixes =
        eventSequenceCountReader.getIndexSuffixesForCurrentSequenceCountIndices();
    final Map<EventSourceType, List<EventSourceEntryDto<?>>> sourcesByType =
        eventCountRequestDto.getEventSources().stream()
            .collect(Collectors.groupingBy(EventSourceEntryDto::getSourceType));
    final List<EventCountResponseDto> matchingEventCounts = new ArrayList<>();
    if (sourcesByType.containsKey(EventSourceType.EXTERNAL)) {
      if (sequenceCountIndexSuffixes.contains(EXTERNAL_EVENTS_INDEX_SUFFIX)) {
        matchingEventCounts.addAll(
            findExternalEventCounts(searchTerm, eventCountRequestDto, sourcesByType));
      } else {
        log.debug(
            "Cannot fetch external event counts as sequence count index for external events does not exist");
      }
    }
    return matchingEventCounts;
  }

  private List<EventCountResponseDto> findExternalEventCounts(
      final String searchTerm,
      final EventCountRequestDto eventCountRequestDto,
      final Map<EventSourceType, List<EventSourceEntryDto<?>>> sourcesByType) {
    final List<EventCountResponseDto> matchingExternalEvents = new ArrayList<>();
    final AtomicBoolean includeAllGroups = new AtomicBoolean(false);
    final List<String> externalGroups =
        sourcesByType.get(EventSourceType.EXTERNAL).stream()
            .map(ExternalEventSourceEntryDto.class::cast)
            .map(
                source -> {
                  if (source.getConfiguration().isIncludeAllGroups()) {
                    includeAllGroups.set(true);
                  }
                  return source.getConfiguration().getGroup();
                })
            .collect(Collectors.toList());
    if (includeAllGroups.get()) {
      matchingExternalEvents.addAll(
          eventSequenceCountReader.getEventCountsForAllExternalEventsUsingSearchTerm(searchTerm));
    } else {
      matchingExternalEvents.addAll(
          eventSequenceCountReader.getEventCountsForExternalGroupsUsingSearchTerm(
              externalGroups, searchTerm));
    }
    if (eligibleForEventSuggestions(sourcesByType)) {
      addSuggestionsForExternalEventCounts(eventCountRequestDto, matchingExternalEvents);
    }
    return matchingExternalEvents;
  }

  private boolean eligibleForEventSuggestions(
      final Map<EventSourceType, List<EventSourceEntryDto<?>>> sourcesByType) {
    final List<EventSourceEntryDto<?>> externalEventSources =
        sourcesByType.get(EventSourceType.EXTERNAL);
    return externalEventSources.size() == 1
        && ((ExternalEventSourceEntryDto) externalEventSources.get(0))
            .getConfiguration()
            .isIncludeAllGroups();
  }

  private void addSuggestionsForExternalEventCounts(
      final EventCountRequestDto eventCountRequestDto,
      final List<EventCountResponseDto> eventCountDtos) {
    if (eventCountRequestDto.getXml() == null
        || eventCountRequestDto.getTargetFlowNodeId() == null
        || eventCountRequestDto.getMappings().isEmpty()) {
      return;
    }

    final Map<String, EventMappingDto> currentMappings = eventCountRequestDto.getMappings();
    final BpmnModelInstance bpmnModelForXml = parseXmlIntoBpmnModel(eventCountRequestDto.getXml());

    validateEventCountSuggestionsParameters(eventCountRequestDto, currentMappings, bpmnModelForXml);

    final FlowNode targetFlowNode =
        bpmnModelForXml.getModelElementById(eventCountRequestDto.getTargetFlowNodeId());
    final Set<EventTypeDto> suggestedEvents =
        getSuggestedExternalEventsForGivenMappings(
            getNearestIncomingMappedEvents(currentMappings, targetFlowNode),
            getNearestOutgoingMappedEvents(currentMappings, targetFlowNode));
    final Set<EventTypeDto> alreadyMappedAndNotTargetNodeEvents =
        currentMappings.keySet().stream()
            .filter(mappedEvent -> !mappedEvent.equals(eventCountRequestDto.getTargetFlowNodeId()))
            .flatMap(
                key ->
                    Stream.of(
                        currentMappings.get(key).getStart(), currentMappings.get(key).getEnd()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    suggestedEvents.removeAll(alreadyMappedAndNotTargetNodeEvents);

    eventCountDtos.stream()
        .filter(eventCountDto -> suggestedEvents.contains(fromEventCountDto(eventCountDto)))
        .forEach(eventCountDto -> eventCountDto.setSuggested(true));
  }

  private BpmnModelInstance parseXmlIntoBpmnModel(final String xmlString) {
    try {
      return BpmnModelUtil.parseBpmnModel(xmlString);
    } catch (final ModelParseException ex) {
      throw new BadRequestException("The provided xml is not valid");
    }
  }

  private Set<EventTypeDto> getSuggestedExternalEventsForGivenMappings(
      final List<EventTypeDto> eventsMappedToIncomingNodes,
      final List<EventTypeDto> eventsMappedToOutgoingNodes) {
    final List<EventSequenceCountDto> suggestedEventSequenceCandidates =
        eventSequenceCountReader.getEventSequencesWithSourceInIncomingOrTargetInOutgoing(
            eventsMappedToIncomingNodes, eventsMappedToOutgoingNodes);

    return suggestedEventSequenceCandidates.stream()
        .filter(sequence -> Objects.nonNull(sequence.getTargetEvent()))
        .map(
            suggestionSequence -> {
              if (eventsMappedToIncomingNodes.contains(suggestionSequence.getSourceEvent())) {
                return suggestionSequence.getTargetEvent();
              } else if (eventsMappedToOutgoingNodes.contains(
                  suggestionSequence.getTargetEvent())) {
                return suggestionSequence.getSourceEvent();
              } else {
                return null;
              }
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private void validateEventCountSuggestionsParameters(
      final EventCountRequestDto eventCountRequestDto,
      final Map<String, EventMappingDto> currentMappings,
      final BpmnModelInstance bpmnModelForXml) {
    final List<FlowNodeDataDto> xmlFlowNodeIds =
        eventCountRequestDto.getXml() == null
            ? Collections.emptyList()
            : extractFlowNodeData(bpmnModelForXml);
    final String targetFlowNodeId = eventCountRequestDto.getTargetFlowNodeId();
    final Set<String> flowNodeIds =
        xmlFlowNodeIds.stream().map(flowNode -> flowNode.getId()).collect(Collectors.toSet());

    if (targetFlowNodeId != null && !flowNodeIds.contains(targetFlowNodeId)) {
      throw new BadRequestException("Target Flow Node IDs must exist within the provided XML");
    }
    if (currentMappings != null && !flowNodeIds.containsAll(currentMappings.keySet())) {
      throw new BadRequestException(
          "All Flow Node IDs for event mappings must exist within the provided XML");
    }
  }

  private List<EventTypeDto> getNearestIncomingMappedEvents(
      final Map<String, EventMappingDto> currentMappings, final FlowNode targetFlowNode) {
    return getNearestMappedFlowNodeIds(
            currentMappings,
            targetFlowNode,
            target ->
                target.getIncoming().stream()
                    .map(SequenceFlow::getSource)
                    .collect(Collectors.toList()))
        .stream()
        .map(Objects.requireNonNull(currentMappings)::get)
        .map(mapping -> Optional.ofNullable(mapping.getEnd()).orElse(mapping.getStart()))
        .collect(Collectors.toList());
  }

  private List<EventTypeDto> getNearestOutgoingMappedEvents(
      final Map<String, EventMappingDto> currentMappings, final FlowNode targetFlowNode) {
    return getNearestMappedFlowNodeIds(
            currentMappings,
            targetFlowNode,
            target ->
                target.getOutgoing().stream()
                    .map(SequenceFlow::getTarget)
                    .collect(Collectors.toList()))
        .stream()
        .map(Objects.requireNonNull(currentMappings)::get)
        .map(mapping -> Optional.ofNullable(mapping.getStart()).orElse(mapping.getEnd()))
        .collect(Collectors.toList());
  }

  /**
   * Mapped Ids at a distance of 1 are first considered. If none are found, we look at a distance of
   * 2. All flow nodes are considered, regardless of whether or not events can be mapped to the
   * element type
   */
  private List<String> getNearestMappedFlowNodeIds(
      final Map<String, EventMappingDto> currentMappings,
      final FlowNode targetFlowNode,
      final Function<FlowNode, List<FlowNode>> getNextFlowNodeCandidates) {
    final List<FlowNode> nearestFlowNodes = getNextFlowNodeCandidates.apply(targetFlowNode);
    List<String> mappedFlowNodeIds =
        findMappedFlowNodeIdsFromList(currentMappings, nearestFlowNodes);
    if (mappedFlowNodeIds.isEmpty()) {
      final List<FlowNode> flowNodeStream =
          nearestFlowNodes.stream()
              .flatMap(flowNode -> getNextFlowNodeCandidates.apply(flowNode).stream())
              .collect(Collectors.toList());
      mappedFlowNodeIds = findMappedFlowNodeIdsFromList(currentMappings, flowNodeStream);
    }
    return mappedFlowNodeIds;
  }

  private List<String> findMappedFlowNodeIdsFromList(
      final Map<String, EventMappingDto> currentMappings, final List<FlowNode> flowNodes) {
    return flowNodes.stream()
        .filter(node -> Objects.requireNonNull(currentMappings).containsKey(node.getId()))
        .map(FlowNode::getId)
        .collect(Collectors.toList());
  }
}
