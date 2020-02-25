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
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessRoleDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingRequestDto;
import org.camunda.optimize.service.es.reader.EventProcessMappingReader;
import org.camunda.optimize.service.es.reader.EventProcessPublishStateReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.es.writer.EventProcessMappingWriter;
import org.camunda.optimize.service.es.writer.EventProcessPublishStateWriter;
import org.camunda.optimize.service.exceptions.InvalidEventProcessStateException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.relations.ReportRelationService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.BpmnModelUtility;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.dto.optimize.rest.ConflictedItemType.COMBINED_REPORT;
import static org.camunda.optimize.dto.optimize.rest.ConflictedItemType.REPORT;
import static org.camunda.optimize.service.util.BpmnModelUtility.extractFlowNodeNames;

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

  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final ConfigurationService configurationService;
  private final ReportService reportService;
  private final ReportRelationService reportRelationService;
  private final CollectionWriter collectionWriter;

  private final EventProcessMappingReader eventProcessMappingReader;
  private final EventProcessMappingWriter eventProcessMappingWriter;

  private final EventProcessPublishStateReader eventProcessPublishStateReader;
  private final EventProcessPublishStateWriter eventProcessPublishStateWriter;

  public boolean isEventProcessImportEnabled() {
    return configurationService.getEventBasedProcessConfiguration().isEnabled();
  }

  public IdDto createEventProcessMapping(final String userId, final EventProcessMappingRequestDto createRequestDto) {
    final EventProcessRoleDto defaultRoleEntry = new EventProcessRoleDto(new IdentityDto(userId, IdentityType.USER));
    final EventProcessMappingDto eventProcessMappingDto = EventProcessMappingDto.builder()
      .name(createRequestDto.getName())
      .xml(createRequestDto.getXml())
      .mappings(createRequestDto.getMappings())
      .lastModifier(userId)
      .eventSources(createRequestDto.getEventSources())
      .roles(Collections.singletonList(defaultRoleEntry))
      .build();
    validateMappingsForProvidedXml(eventProcessMappingDto);
    validateEventSources(userId, eventProcessMappingDto);
    return eventProcessMappingWriter.createEventProcessMapping(eventProcessMappingDto);
  }

  public void updateEventProcessMapping(final String userId,
                                        final String eventProcessId,
                                        final EventProcessMappingRequestDto updateRequest) {
    final EventProcessMappingDto eventProcessMappingDto = EventProcessMappingDto.builder()
      .id(eventProcessId)
      .name(updateRequest.getName())
      .xml(updateRequest.getXml())
      .mappings(updateRequest.getMappings())
      .lastModifier(userId)
      .eventSources(updateRequest.getEventSources())
      .build();
    validateMappingsForProvidedXml(eventProcessMappingDto);
    validateEventSources(userId, eventProcessMappingDto);
    eventProcessMappingWriter.updateEventProcessMapping(eventProcessMappingDto);
  }

  public ConflictResponseDto getDeleteConflictingItems(final String eventProcessId) {
    List<ReportDefinitionDto> reportsForProcessDefinitionKey = reportService.getAllReportsForProcessDefinitionKey(
      eventProcessId);
    Set<ConflictedItemDto> conflictedItems = new HashSet<>();
    for (ReportDefinitionDto reportForEventProcess : reportsForProcessDefinitionKey) {
      if (reportForEventProcess instanceof CombinedReportDefinitionDto) {
        conflictedItems.add(new ConflictedItemDto(
          reportForEventProcess.getId(),
          COMBINED_REPORT,
          reportForEventProcess.getName()
        ));
      } else {
        conflictedItems.add(new ConflictedItemDto(
          reportForEventProcess.getId(),
          REPORT,
          reportForEventProcess.getName()
        ));
      }
      conflictedItems.addAll(reportRelationService.getConflictedItemsForDeleteReport(reportForEventProcess));
    }
    return new ConflictResponseDto(conflictedItems);
  }

  public boolean deleteEventProcessMapping(final String eventProcessMappingId) {
    reportService.deleteAllReportsForProcessDefinitionKey(eventProcessMappingId);
    collectionWriter.deleteScopeEntryFromAllCollections(CollectionScopeEntryDto.convertTypeAndKeyToScopeEntryId(
      PROCESS, eventProcessMappingId));
    eventProcessPublishStateWriter.deleteAllEventProcessPublishStatesForEventProcessMappingId(eventProcessMappingId);
    return eventProcessMappingWriter.deleteEventProcessMapping(eventProcessMappingId);
  }

  public EventProcessMappingDto getEventProcessMapping(final String userId, final String eventProcessMappingId) {
    final Optional<EventProcessMappingDto> eventProcessMapping = eventProcessMappingReader
      .getEventProcessMapping(eventProcessMappingId);

    eventProcessMapping.ifPresent(eventProcessMappingDto -> assignState(
      eventProcessMappingDto,
      id -> eventProcessPublishStateReader.getEventProcessPublishStateByEventProcessId(id).orElse(null)
    ));

    eventProcessMapping.ifPresent(eventProcessMappingDto ->
                                    validateAccessToCamundaEventSourcesOrFail(userId, eventProcessMappingDto));

    return eventProcessMapping.orElseThrow(() -> {
      final String message = String.format(
        "Event based process does not exist! Tried to retrieve event based process with id: %s.", eventProcessMappingId
      );
      return new NotFoundException(message);
    });
  }

  public List<EventProcessMappingDto> getAllEventProcessMappingsOmitXml(final String userId) {
    final Map<String, EventProcessPublishStateDto> allPublishedStates =
      eventProcessPublishStateReader.getAllEventProcessPublishStatesWithDeletedState(false)
        .stream()
        .collect(Collectors.toMap(EventProcessPublishStateDto::getProcessMappingId, Function.identity()));

    List<EventProcessMappingDto> allEventProcessMappingsOmitXml =
      eventProcessMappingReader.getAllEventProcessMappingsOmitXml();

    allEventProcessMappingsOmitXml
      .forEach(eventProcessMappingDto -> assignState(eventProcessMappingDto, allPublishedStates::get));

    // filter by user authorization for event sources
    allEventProcessMappingsOmitXml = filterEventProcessMappingsByEventSourceAuthorizations(
      userId,
      allEventProcessMappingsOmitXml
    );

    return allEventProcessMappingsOmitXml;
  }

  public void publishEventProcessMapping(final String userId, final String eventProcessMappingId) {
    final EventProcessMappingDto eventProcessMapping = getEventProcessMapping(userId, eventProcessMappingId);

    if (!PUBLISHABLE_STATES.contains(eventProcessMapping.getState())) {
      throw new InvalidEventProcessStateException(
        "Cannot publish event based process from state: " + eventProcessMapping.getState()
      );
    }
    if (eventProcessMapping.getEventSources().isEmpty()) {
      throw new OptimizeValidationException("Cannot publish event based process with no data sources");
    }

    final EventProcessPublishStateDto processPublishState = EventProcessPublishStateDto
      .builder()
      .processMappingId(eventProcessMapping.getId())
      .name(eventProcessMapping.getName())
      .xml(eventProcessMapping.getXml())
      .publishDateTime(LocalDateUtil.getCurrentDateTime())
      .state(EventProcessState.PUBLISH_PENDING)
      .publishProgress(0.0D)
      .deleted(false)
      .mappings(eventProcessMapping.getMappings())
      .eventImportSources(eventProcessMapping.getEventSources().stream()
                            .map(this::createEventImportSourceFromDataSource)
                            .collect(toList()))
      .build();

    eventProcessPublishStateWriter.deleteAllEventProcessPublishStatesForEventProcessMappingId(eventProcessMappingId);
    eventProcessPublishStateWriter.createEventProcessPublishState(processPublishState);
  }

  private EventImportSourceDto createEventImportSourceFromDataSource(EventSourceEntryDto eventSourceEntryDto) {
    return EventImportSourceDto.builder()
      .lastImportedEventTimestamp(OffsetDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.systemDefault()))
      .eventSource(eventSourceEntryDto)
      .build();
  }

  public void cancelPublish(final String userId, final String eventProcessMappingId) {
    final EventProcessMappingDto eventProcessMapping = getEventProcessMapping(userId, eventProcessMappingId);

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

  private List<EventProcessMappingDto> filterEventProcessMappingsByEventSourceAuthorizations(
    final String userId, final List<EventProcessMappingDto> eventMappings) {
    // only return mappings where the user has access to all event sources
    return eventMappings.stream()
      .filter(eventMapping ->
                eventMapping.getEventSources()
                  .stream()
                  .allMatch(eventSource -> validateEventSourceAuthorisation(userId, eventSource)))
      .collect(toList());
  }

  private void validateEventSources(final String userId, final EventProcessMappingDto eventProcessMappingDto) {
    final List<EventSourceEntryDto> eventSources = eventProcessMappingDto.getEventSources();
    if (eventSources == null || eventSources.contains(null)) {
      throw new OptimizeValidationException("Sources for an event based process cannot be null");
    }
    validateAccessToCamundaEventSourcesOrFail(userId, eventProcessMappingDto);
    validateNoDuplicateCamundaEventSources(eventProcessMappingDto);
    validateNoDuplicateExternalEventSources(eventProcessMappingDto);
  }


  private boolean validateEventSourceAuthorisation(final String userId, final EventSourceEntryDto eventSource) {
    return eventSource.getType().equals(EventSourceType.EXTERNAL)
      || definitionAuthorizationService.isAuthorizedToSeeDefinition(
      userId,
      IdentityType.USER,
      eventSource.getProcessDefinitionKey(),
      PROCESS,
      eventSource.getTenants()
    );
  }

  private void validateAccessToCamundaEventSourcesOrFail(final String userId,
                                                         final EventProcessMappingDto eventProcessMappingDto) {
    final Set<String> notAuthorizedProcesses = eventProcessMappingDto.getEventSources().stream()
      .filter(eventSource -> !validateEventSourceAuthorisation(userId, eventSource))
      .map(EventSourceEntryDto::getProcessDefinitionKey)
      .collect(Collectors.toSet());
    if (!notAuthorizedProcesses.isEmpty()) {
      final String errorMessage = String.format(
        "The user is not authorized to access the following process definitions in the event sources: %s",
        notAuthorizedProcesses
      );
      throw new ForbiddenException(errorMessage);
    }
  }

  private void validateNoDuplicateCamundaEventSources(final EventProcessMappingDto eventProcessMappingDto) {
    Set<String> processDefinitionKeys = new HashSet<>();
    final Set<String> duplicates = eventProcessMappingDto.getEventSources()
      .stream()
      .filter(eventSourceEntryDto -> EventSourceType.CAMUNDA.equals(eventSourceEntryDto.getType()))
      .map(EventSourceEntryDto::getProcessDefinitionKey)
      .filter(Objects::nonNull)
      .filter(key -> !processDefinitionKeys.add(key))
      .collect(toSet());
    if (!duplicates.isEmpty()) {
      final String errorMessage = String.format(
        "Only one event source for each process definition can exist for an Event Process Mapping." +
          "Mapping with id [%s] contains duplicates for process definition keys [%s]",
        eventProcessMappingDto.getId(), duplicates
      );
      throw new OptimizeConflictException(errorMessage);
    }
  }

  private void validateNoDuplicateExternalEventSources(final EventProcessMappingDto eventProcessMappingDto) {
    final long numberOfExternalEventSources = eventProcessMappingDto.getEventSources()
      .stream()
      .filter(eventSourceEntryDto -> EventSourceType.EXTERNAL.equals(eventSourceEntryDto.getType()))
      .count();
    if (numberOfExternalEventSources > 1) {

      throw new OptimizeConflictException(String.format(
        "Mapping can only contain one external event sources but %s were provided.",
        numberOfExternalEventSources
      ));
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
    Map<String, EventMappingDto> eventMappings = eventProcessMappingDto.getMappings();

    if (eventMappings != null) {
      if (!flowNodeIds.containsAll(eventMappings.keySet())) {
        throw new BadRequestException("All Flow Node IDs for event mappings must exist within the provided XML");
      }
      if (eventMappings.entrySet().stream()
        .anyMatch(mapping -> mapping.getValue().getStart() == null && mapping.getValue().getEnd() == null)) {
        throw new BadRequestException("All Flow Node mappings provided must have either a start or end event mapped");
      }
    }
  }

}
