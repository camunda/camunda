/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service;

import com.google.common.collect.ImmutableSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.service.engine.importing.BpmnModelUtility;
import org.camunda.optimize.service.es.reader.EventProcessMappingReader;
import org.camunda.optimize.service.es.writer.EventProcessMappingWriter;
import org.camunda.optimize.service.exceptions.InvalidEventProcessStateException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.engine.importing.BpmnModelUtility.extractFlowNodeNames;
import static org.camunda.optimize.service.engine.importing.BpmnModelUtility.extractUserTaskNames;
import static org.camunda.optimize.service.engine.importing.BpmnModelUtility.parseBpmnModel;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventProcessMappingService {

  public static final ImmutableSet<EventProcessState> PUBLISHABLE_STATES = ImmutableSet.of(
    EventProcessState.MAPPED, EventProcessState.UNPUBLISHED_CHANGES
  );
  public static final ImmutableSet<EventProcessState> PUBLISH_CANCELABLE_STATES = ImmutableSet.of(
    EventProcessState.PUBLISH_PENDING, EventProcessState.PUBLISHED, EventProcessState.UNPUBLISHED_CHANGES
  );

  private final EventProcessMappingReader eventProcessMappingReader;
  private final EventProcessMappingWriter eventProcessMappingWriter;

  private final EventProcessDefinitionService eventProcessDefinitionService;

  public IdDto createEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto) {
    validateMappingsForProvidedXml(eventProcessMappingDto);
    return eventProcessMappingWriter.createEventProcessMapping(eventProcessMappingDto);
  }

  public void updateEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto) {
    validateMappingsForProvidedXml(eventProcessMappingDto);
    eventProcessMappingWriter.updateEventProcessMapping(eventProcessMappingDto);
  }

  public boolean deleteEventProcessMapping(final String eventProcessMappingId) {
    eventProcessDefinitionService.deleteEventProcessDefinition(eventProcessMappingId);
    return eventProcessMappingWriter.deleteEventProcessMapping(eventProcessMappingId);
  }

  public EventProcessMappingDto getEventProcessMapping(final String eventProcessMappingId) {
    final Optional<EventProcessMappingDto> eventBasedProcess = eventProcessMappingReader
        .getEventProcessMapping(eventProcessMappingId);

    eventBasedProcess.ifPresent(eventProcessMappingDto -> assignState(
        eventProcessMappingDto,
        id -> eventProcessDefinitionService.getEventProcessDefinition(id).orElse(null)
    ));

    return eventBasedProcess.orElseThrow(() -> {
      final String message = String.format(
        "Event based process does not exist! Tried to retrieve event based process with id: %s.", eventProcessMappingId
      );
      return new NotFoundException(message);
    });
  }

  public List<EventProcessMappingDto> getAllEventProcessMappingsOmitXml() {
    final Map<String, EventProcessDefinitionDto> allPublishedDefinitions =
        eventProcessDefinitionService.getAllEventProcessesDefinitionsOmitXml()
            .stream().collect(Collectors.toMap(EventProcessDefinitionDto::getKey, Function.identity()));

    final List<EventProcessMappingDto> allEventProcessMappingsOmitXml =
        eventProcessMappingReader.getAllEventProcessMappingsOmitXml();

    allEventProcessMappingsOmitXml
        .forEach(eventProcessMappingDto -> assignState(eventProcessMappingDto, allPublishedDefinitions::get));

    return allEventProcessMappingsOmitXml;
  }

  public void publishEventProcessMapping(final String eventProcessMappingId) {
    final EventProcessMappingDto eventProcessMapping = getEventProcessMapping(eventProcessMappingId);

    if (!PUBLISHABLE_STATES.contains(eventProcessMapping.getState())) {
      throw new InvalidEventProcessStateException(
          "Cannot publish event based process from state: " + eventProcessMapping.getState()
      );
    }

    final BpmnModelInstance bpmnModelInstance = parseBpmnModel(eventProcessMapping.getXml());
    final EventProcessDefinitionDto eventProcessMappingDtoToPublish = EventProcessDefinitionDto
        .eventProcessBuilder()
        .id(eventProcessMapping.getId())
        .key(eventProcessMapping.getId())
        .name(eventProcessMapping.getName())
        .version("1")
        .bpmn20Xml(eventProcessMapping.getXml())
        .flowNodeNames(extractFlowNodeNames(bpmnModelInstance))
        .userTaskNames(extractUserTaskNames(bpmnModelInstance))
        // Note: mappings are not available in this DTO yet but will be with OPT-2982
        .createdDateTime(LocalDateUtil.getCurrentDateTime())
        .build();
    eventProcessDefinitionService.createEventProcessDefinition(eventProcessMappingDtoToPublish);
  }

  public void cancelPublish(final String eventProcessMappingId) {
    final EventProcessMappingDto eventProcessMapping = getEventProcessMapping(eventProcessMappingId);

    if (!PUBLISH_CANCELABLE_STATES.contains(eventProcessMapping.getState())) {
      throw new InvalidEventProcessStateException(
          "Cannot cancel publishing of event based process from state: " + eventProcessMapping.getState()
      );
    }

    final boolean publishedDecisionWasDeleted = eventProcessDefinitionService.deleteEventProcessDefinition(
        eventProcessMappingId
    );

    if (!publishedDecisionWasDeleted) {
      final String message = String.format(
          "Cannot cancel publishing of an event based process with key [%s] as it is not published yet.",
          eventProcessMappingId
      );
      throw new OptimizeValidationException(message);
    }
  }

  private void assignState(final EventProcessMappingDto eventProcessMappingDto,
                           final Function<String, EventProcessDefinitionDto> definitionProvider) {
    if (MapUtils.isEmpty(eventProcessMappingDto.getMappings())) {
      eventProcessMappingDto.setState(EventProcessState.UNMAPPED);
    } else {
      eventProcessMappingDto.setState(EventProcessState.MAPPED);
    }

    Optional.ofNullable(definitionProvider.apply(eventProcessMappingDto.getId()))
        .ifPresent(publishedDefinition -> {
          if (publishedDefinition.getCreatedDateTime().isAfter(eventProcessMappingDto.getLastModified())) {
            // with OPT-2982 this will become dependent on the publishing progress, once finished it will be
            // published
            eventProcessMappingDto.setState(EventProcessState.PUBLISH_PENDING);
            eventProcessMappingDto.setPublishingProgress(0.0D);
          } else {
            eventProcessMappingDto.setState(EventProcessState.UNPUBLISHED_CHANGES);
          }
        });
  }

  private void validateMappingsForProvidedXml(final EventProcessMappingDto eventProcessMappingDto) {
    Set<String> flowNodeIds = eventProcessMappingDto.getXml() == null ? Collections.emptySet() :
        extractFlowNodeNames(BpmnModelUtility.parseBpmnModel(
            eventProcessMappingDto.getXml())).keySet();
    if (eventProcessMappingDto.getMappings() != null
        && !flowNodeIds.containsAll(eventProcessMappingDto.getMappings().keySet())) {
      throw new BadRequestException("All Flow Node IDs for event mappings must exist within the provided XML");
    }
  }

}
