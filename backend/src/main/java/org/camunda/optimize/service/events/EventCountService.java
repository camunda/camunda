/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.ModelParseException;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.EventSequenceCountReader;
import org.camunda.optimize.service.util.BpmnModelUtil;
import org.camunda.optimize.service.util.EventDtoBuilderUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.util.ArrayList;
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

import static org.camunda.optimize.service.util.BpmnModelUtil.extractFlowNodeNames;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaProcessInstanceEndEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaProcessInstanceStartEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.fromEventCountDto;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

@Slf4j
@Component
public class EventCountService {

  private final EventSequenceCountReader eventSequenceCountReader;
  private final CamundaEventService camundaEventService;

  public EventCountService(final CamundaEventService camundaEventService,
                           final OptimizeElasticsearchClient esClient,
                           final ObjectMapper objectMapper,
                           final ConfigurationService configurationService) {
    this.camundaEventService = camundaEventService;
    this.eventSequenceCountReader = new EventSequenceCountReader(
      EXTERNAL_EVENTS_INDEX_SUFFIX,
      esClient,
      objectMapper,
      configurationService
    );
  }

  public List<EventCountResponseDto> getEventCounts(final String userId,
                                                    final String searchTerm,
                                                    final EventCountRequestDto eventCountRequestDto) {
    if (eventCountRequestDto == null || eventCountRequestDto.getEventSources().isEmpty()) {
      return Collections.emptyList();
    }

    final Set<String> sequenceCountIndexSuffixes =
      eventSequenceCountReader.getIndexSuffixesForCurrentSequenceCountIndices();
    final Map<EventSourceType, List<EventSourceEntryDto>> sourcesByType = eventCountRequestDto.getEventSources()
      .stream()
      .collect(Collectors.groupingBy(EventSourceEntryDto::getType));
    final List<EventCountResponseDto> matchingEventCounts = new ArrayList<>();
    if (sourcesByType.containsKey(EventSourceType.EXTERNAL)) {
      if (sequenceCountIndexSuffixes.contains(EXTERNAL_EVENTS_INDEX_SUFFIX)) {
        matchingEventCounts.addAll(eventSequenceCountReader.getEventCountsWithSearchTerm(searchTerm));
        if (eligibleForEventSuggestions(sourcesByType)) {
          addSuggestionsForExternalEventCounts(eventCountRequestDto, matchingEventCounts);
        }
      } else {
        log.debug("Cannot fetch external event counts as sequence count index for external events does not exist");
      }
    }
    if (sourcesByType.containsKey(EventSourceType.CAMUNDA)) {
      final List<EventSourceEntryDto> camundaSources = sourcesByType.get(EventSourceType.CAMUNDA);
      final List<EventSourceEntryDto> sourcesWithSequenceIndex = camundaSources.stream()
        .filter(source -> sequenceCountIndexSuffixes.contains(source.getProcessDefinitionKey()))
        .collect(Collectors.toList());
      if (camundaSources.size() != sourcesWithSequenceIndex.size()) {
        log.debug(
          "Cannot fetch event counts for sources with keys {} as sequence count indices do not exist",
          camundaSources.stream()
            .filter(source -> !sequenceCountIndexSuffixes.contains(source.getProcessDefinitionKey()))
            .map(EventSourceEntryDto::getProcessDefinitionKey)
            .collect(Collectors.toList())
        );
      }
      final Map<EventTypeDto, Long> allCamundaSourcesEventCounts =
        eventSequenceCountReader.getEventCountsForCamundaSources(sourcesWithSequenceIndex)
          .stream()
          .collect(Collectors.toMap(EventDtoBuilderUtil::fromEventCountDto, EventCountResponseDto::getCount));
      final List<EventCountResponseDto> countedCamundaEvents = camundaSources.stream()
        .map(source -> getEventsForCamundaProcess(userId, searchTerm, source, allCamundaSourcesEventCounts))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
      matchingEventCounts.addAll(countedCamundaEvents);
    }
    return matchingEventCounts;
  }

  private boolean eligibleForEventSuggestions(final Map<EventSourceType, List<EventSourceEntryDto>> sourcesByType) {
    return !sourcesByType.containsKey(EventSourceType.CAMUNDA)
      && sourcesByType.get(EventSourceType.EXTERNAL).size() == 1;
  }

  private List<EventCountResponseDto> getEventsForCamundaProcess(final String userId,
                                                                 final String searchTerm,
                                                                 final EventSourceEntryDto eventSourceEntryDto,
                                                                 final Map<EventTypeDto, Long> typeAndCount) {
    return camundaEventService
      .getLabeledCamundaEventTypesForProcess(userId, eventSourceEntryDto)
      .stream()
      .filter(eventDto -> searchTerm == null
        || StringUtils.containsIgnoreCase(eventDto.getEventName(), searchTerm)
        || StringUtils.containsIgnoreCase(eventDto.getEventLabel(), searchTerm)
        || StringUtils.containsIgnoreCase(eventDto.getGroup(), searchTerm)
        || StringUtils.containsIgnoreCase(eventDto.getSource(), searchTerm))
      .map(labeledEventTypeDto ->
             EventCountResponseDto.builder()
               .source(labeledEventTypeDto.getSource())
               .group(labeledEventTypeDto.getGroup())
               .eventName(labeledEventTypeDto.getEventName())
               .eventLabel(labeledEventTypeDto.getEventLabel())
               .count(findEventCountForCamundaEvent(eventSourceEntryDto, typeAndCount, labeledEventTypeDto))
               .build()
      )
      .collect(Collectors.toList());
  }

  private Long findEventCountForCamundaEvent(final EventSourceEntryDto eventSourceEntryDto,
                                             final Map<EventTypeDto, Long> typeAndCount,
                                             final EventTypeDto labeledEventTypeDto) {
    return Optional.ofNullable(typeAndCount.get(labeledEventTypeDto))
      .orElseGet(() -> isProcessInstanceStartEndEvent(eventSourceEntryDto, labeledEventTypeDto) ? null : 0L);
  }

  private boolean isProcessInstanceStartEndEvent(final EventSourceEntryDto eventSourceEntryDto,
                                                 final EventTypeDto labeledEventTypeDto) {
    return labeledEventTypeDto.getEventName().equalsIgnoreCase(
      applyCamundaProcessInstanceStartEventSuffix(eventSourceEntryDto.getProcessDefinitionKey()))
      || labeledEventTypeDto.getEventName().equalsIgnoreCase(
      applyCamundaProcessInstanceEndEventSuffix(eventSourceEntryDto.getProcessDefinitionKey()));
  }

  private void addSuggestionsForExternalEventCounts(final EventCountRequestDto eventCountRequestDto,
                                                    final List<EventCountResponseDto> eventCountDtos) {
    if (eventCountRequestDto.getXml() == null
      || eventCountRequestDto.getTargetFlowNodeId() == null
      || eventCountRequestDto.getMappings().isEmpty()) {
      return;
    }

    final Map<String, EventMappingDto> currentMappings = eventCountRequestDto.getMappings();
    final BpmnModelInstance bpmnModelForXml = parseXmlIntoBpmnModel(eventCountRequestDto.getXml());

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
      .filter(eventCountDto -> suggestedEvents.contains(fromEventCountDto(eventCountDto)))
      .forEach(eventCountDto -> eventCountDto.setSuggested(true));
  }

  private BpmnModelInstance parseXmlIntoBpmnModel(final String xmlString) {
    try {
      return BpmnModelUtil.parseBpmnModel(xmlString);
    } catch (ModelParseException ex) {
      throw new BadRequestException("The provided xml is not valid");
    }
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
