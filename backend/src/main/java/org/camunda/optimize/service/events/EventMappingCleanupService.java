/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import org.camunda.optimize.service.util.BpmnModelUtility;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class EventMappingCleanupService {
  private final EventCountService eventCountService;

  public Map<String, EventMappingDto> doMappingCleanup(final String userId,
                                                       final EventMappingCleanupRequestDto requestDto) {
    final Set<EventTypeDto> availableEventTypeDtos = eventCountService
      .getEventCounts(userId, null, mapToEventCountRequest(requestDto))
      .stream()
      .map(this::mapToEventTypeDto)
      .collect(Collectors.toSet());

    final Set<String> currentModelFlowNodeIds = BpmnModelUtility.extractFlowNodeNames(requestDto.getXml()).keySet();
    return requestDto.getMappings().entrySet()
      .stream()
      .filter(mappingEntry -> currentModelFlowNodeIds.contains(mappingEntry.getKey()))
      .peek(entry -> cleanupOutOfSourceScopeMappings(entry.getValue(), availableEventTypeDtos))
      .filter(mappingEntry -> mappingEntry.getValue().getStart() != null || mappingEntry.getValue().getEnd() != null)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private EventCountRequestDto mapToEventCountRequest(final EventMappingCleanupRequestDto requestDto) {
    return EventCountRequestDto.builder()
      .eventSources(requestDto.getEventSources())
      .targetFlowNodeId(null)
      .xml(requestDto.getXml())
      .mappings(requestDto.getMappings())
      .build();
  }

  private EventTypeDto mapToEventTypeDto(final EventCountDto eventCountDto) {
    return EventTypeDto.builder()
      .source(eventCountDto.getSource())
      .group(eventCountDto.getGroup())
      .eventName(eventCountDto.getEventName())
      .build();
  }

  private void cleanupOutOfSourceScopeMappings(final EventMappingDto mappingEntry,
                                               final Set<EventTypeDto> availableEvents) {
    Optional.ofNullable(mappingEntry.getStart())
      .ifPresent(startMapping -> {
        if (!availableEvents.contains(startMapping)) {
          mappingEntry.setStart(null);
        }
      });
    Optional.ofNullable(mappingEntry.getEnd())
      .ifPresent(endMapping -> {
        if (!availableEvents.contains(endMapping)) {
          mappingEntry.setEnd(null);
        }
      });
  }

}
