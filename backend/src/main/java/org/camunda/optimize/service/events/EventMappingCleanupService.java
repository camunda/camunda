/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.xml.ModelParseException;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventMappingCleanupRequestDto;
import org.camunda.optimize.service.util.BpmnModelUtil;
import org.camunda.optimize.service.util.EventDtoBuilderUtil;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
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
      .map(EventDtoBuilderUtil::fromEventCountDto)
      .collect(Collectors.toSet());

    final Set<String> currentModelFlowNodeIds =
      BpmnModelUtil.extractFlowNodeData(parseXmlIntoBpmnModel(requestDto.getXml()))
        .stream()
        .map(FlowNodeDataDto::getId)
        .collect(Collectors.toSet());
    return requestDto.getMappings().entrySet()
      .stream()
      .filter(mappingEntry -> currentModelFlowNodeIds.contains(mappingEntry.getKey()))
      .peek(entry -> cleanupOutOfSourceScopeMappings(entry.getValue(), availableEventTypeDtos))
      .filter(mappingEntry -> mappingEntry.getValue().getStart() != null || mappingEntry.getValue().getEnd() != null)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private BpmnModelInstance parseXmlIntoBpmnModel(final String xmlString) {
    try {
      return BpmnModelUtil.parseBpmnModel(xmlString);
    } catch (ModelParseException ex) {
      throw new BadRequestException("The provided xml is not valid");
    }
  }

  private EventCountRequestDto mapToEventCountRequest(final EventMappingCleanupRequestDto requestDto) {
    return EventCountRequestDto.builder()
      .eventSources(requestDto.getEventSources())
      .targetFlowNodeId(null)
      .xml(requestDto.getXml())
      .mappings(requestDto.getMappings())
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
