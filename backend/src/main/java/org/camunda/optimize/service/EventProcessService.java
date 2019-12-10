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
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.service.engine.importing.BpmnModelUtility;
import org.camunda.optimize.service.es.reader.EventProcessMappingReader;
import org.camunda.optimize.service.es.reader.EventProcessPublishStateReader;
import org.camunda.optimize.service.es.writer.EventProcessMappingWriter;
import org.camunda.optimize.service.es.writer.EventProcessPublishStateWriter;
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

@RequiredArgsConstructor
@Component
@Slf4j
public class EventProcessService {

  public static final ImmutableSet<EventProcessState> PUBLISHABLE_STATES = ImmutableSet.of(
    EventProcessState.MAPPED, EventProcessState.UNPUBLISHED_CHANGES
  );
  public static final ImmutableSet<EventProcessState> PUBLISH_CANCELABLE_STATES = ImmutableSet.of(
    EventProcessState.PUBLISH_PENDING, EventProcessState.PUBLISHED, EventProcessState.UNPUBLISHED_CHANGES
  );

  private final EventProcessMappingReader eventProcessMappingReader;
  private final EventProcessMappingWriter eventProcessMappingWriter;

  private final EventProcessPublishStateReader eventProcessPublishStateReader;
  private final EventProcessPublishStateWriter eventProcessPublishStateWriter;

  public IdDto createEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto) {
    validateMappingsForProvidedXml(eventProcessMappingDto);
    return eventProcessMappingWriter.createEventProcessMapping(eventProcessMappingDto);
  }

  public void updateEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto) {
    validateMappingsForProvidedXml(eventProcessMappingDto);
    eventProcessMappingWriter.updateEventProcessMapping(eventProcessMappingDto);
  }

  public boolean deleteEventProcessMapping(final String eventProcessMappingId) {
    eventProcessPublishStateWriter.deleteAllEventProcessPublishStatesForEventProcessMappingId(eventProcessMappingId);
    return eventProcessMappingWriter.deleteEventProcessMapping(eventProcessMappingId);
  }

  public EventProcessMappingDto getEventProcessMapping(final String eventProcessMappingId) {
    final Optional<EventProcessMappingDto> eventProcessMapping = eventProcessMappingReader
      .getEventProcessMapping(eventProcessMappingId);

    eventProcessMapping.ifPresent(eventProcessMappingDto -> assignState(
      eventProcessMappingDto,
      id -> eventProcessPublishStateReader.getEventProcessPublishState(id).orElse(null)
    ));

    return eventProcessMapping.orElseThrow(() -> {
      final String message = String.format(
        "Event based process does not exist! Tried to retrieve event based process with id: %s.", eventProcessMappingId
      );
      return new NotFoundException(message);
    });
  }

  public List<EventProcessMappingDto> getAllEventProcessMappingsOmitXml() {
    final Map<String, EventProcessPublishStateDto> allPublishedStates =
      eventProcessPublishStateReader.getAllEventProcessPublishStatesWithDeletedState(false)
        .stream()
        .collect(Collectors.toMap(EventProcessPublishStateDto::getProcessId, Function.identity()));

    final List<EventProcessMappingDto> allEventProcessMappingsOmitXml =
      eventProcessMappingReader.getAllEventProcessMappingsOmitXml();

    allEventProcessMappingsOmitXml
      .forEach(eventProcessMappingDto -> assignState(eventProcessMappingDto, allPublishedStates::get));

    return allEventProcessMappingsOmitXml;
  }

  public void publishEventProcessMapping(final String eventProcessMappingId) {
    final EventProcessMappingDto eventProcessMapping = getEventProcessMapping(eventProcessMappingId);

    if (!PUBLISHABLE_STATES.contains(eventProcessMapping.getState())) {
      throw new InvalidEventProcessStateException(
        "Cannot publish event based process from state: " + eventProcessMapping.getState()
      );
    }

    final EventProcessPublishStateDto processPublishState = EventProcessPublishStateDto
      .builder()
      .processId(eventProcessMapping.getId())
      .xml(eventProcessMapping.getXml())
      .publishDateTime(LocalDateUtil.getCurrentDateTime())
      .state(EventProcessState.PUBLISH_PENDING)
      .publishProgress(0.0D)
      .deleted(false)
      .mappings(eventProcessMapping.getMappings())
      .build();

    eventProcessPublishStateWriter.createEventProcessPublishState(processPublishState);
  }

  public void cancelPublish(final String eventProcessMappingId) {
    final EventProcessMappingDto eventProcessMapping = getEventProcessMapping(eventProcessMappingId);

    if (!PUBLISH_CANCELABLE_STATES.contains(eventProcessMapping.getState())) {
      throw new InvalidEventProcessStateException(
        "Cannot cancel publishing of event based process from state: " + eventProcessMapping.getState()
      );
    }

    final boolean publishWasCancelledSuccessfully = eventProcessPublishStateWriter
      .deleteAllEventProcessPublishStatesForEventProcessMappingId(eventProcessMappingId);

    if (!publishWasCancelledSuccessfully) {
      final String message = String.format(
        "Cannot cancel publishing of an event based process with key [%s] as it is not published yet.",
        eventProcessMappingId
      );
      throw new OptimizeValidationException(message);
    }
  }

  private void assignState(final EventProcessMappingDto eventProcessMappingDto,
                           final Function<String, EventProcessPublishStateDto> publishStateReader) {
    if (MapUtils.isEmpty(eventProcessMappingDto.getMappings())) {
      eventProcessMappingDto.setState(EventProcessState.UNMAPPED);
    } else {
      eventProcessMappingDto.setState(EventProcessState.MAPPED);
    }

    Optional.ofNullable(publishStateReader.apply(eventProcessMappingDto.getId()))
      .ifPresent(processPublishStateDto -> {
        if (processPublishStateDto.getPublishDateTime().isAfter(eventProcessMappingDto.getLastModified())) {
          eventProcessMappingDto.setState(processPublishStateDto.getState());
          eventProcessMappingDto.setPublishingProgress(processPublishStateDto.getPublishProgress());
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
