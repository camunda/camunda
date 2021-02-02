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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.Event;
import org.camunda.bpm.model.xml.ModelParseException;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.AutogenerationProcessModelDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingRequestDto;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.es.reader.EventProcessMappingReader;
import org.camunda.optimize.service.es.reader.EventProcessPublishStateReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.es.writer.EventProcessMappingWriter;
import org.camunda.optimize.service.es.writer.EventProcessPublishStateWriter;
import org.camunda.optimize.service.events.CamundaEventService;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.events.autogeneration.AutogenerationProcessModelService;
import org.camunda.optimize.service.exceptions.InvalidEventProcessStateException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.relations.ReportRelationService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.EngineDefinitionAuthorizationService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.BpmnModelUtil;
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
import static org.camunda.optimize.service.util.BpmnModelUtil.extractFlowNodeNames;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventProcessService {

  private static final ImmutableSet<EventProcessState> PUBLISHABLE_STATES = ImmutableSet.of(
    EventProcessState.MAPPED, EventProcessState.UNPUBLISHED_CHANGES
  );
  private static final ImmutableSet<EventProcessState> PUBLISH_CANCELABLE_STATES = ImmutableSet.of(
    EventProcessState.PUBLISH_PENDING, EventProcessState.PUBLISHED, EventProcessState.UNPUBLISHED_CHANGES
  );
  public static final String DEFAULT_AUTOGENERATED_PROCESS_NAME = "Autogenerated Process";
  private static final String DEFAULT_PROCESS_NAME = "New Process";

  private final EngineDefinitionAuthorizationService definitionAuthorizationService;
  private final EventProcessDefinitionService eventProcessDefinitionService;
  private final ReportService reportService;
  private final ReportRelationService reportRelationService;
  private final CollectionWriter collectionWriter;
  private final ConfigurationService configurationService;

  private final ExternalEventService externalEventService;
  private final CamundaEventService camundaEventService;
  private final AutogenerationProcessModelService autogenerationProcessModelService;

  private final EventProcessMappingReader eventProcessMappingReader;
  private final EventProcessMappingWriter eventProcessMappingWriter;

  private final CamundaActivityEventReader camundaActivityEventReader;

  private final EventProcessPublishStateReader eventProcessPublishStateReader;
  private final EventProcessPublishStateWriter eventProcessPublishStateWriter;

  public IdResponseDto createEventProcessMapping(final String userId,
                                                 final EventProcessMappingCreateRequestDto createRequestDto) {
    if (createRequestDto.isAutogenerate()) {
      return autogenerateEventProcessMapping(userId, createRequestDto);
    }
    final EventProcessMappingDto eventProcessMappingDto =
      EventProcessMappingCreateRequestDto.to(userId, createRequestDto);
    validateMappingsAndXmlCompatibility(eventProcessMappingDto);
    validateEventSources(userId, eventProcessMappingDto);
    return eventProcessMappingWriter.createEventProcessMapping(eventProcessMappingDto);
  }

  private IdResponseDto autogenerateEventProcessMapping(final String userId,
                                                        final EventProcessMappingRequestDto autogenerationRequest) {
    final List<EventSourceEntryDto<?>> eventSources = autogenerationRequest.getEventSources();
    if (eventSources.isEmpty()) {
      throw new OptimizeValidationException("Autogeneration requires at least one event source");
    }
    final EventProcessMappingDto eventProcessMappingDto = EventProcessMappingDto.builder()
      .name(DEFAULT_AUTOGENERATED_PROCESS_NAME)
      .lastModifier(userId)
      .eventSources(eventSources)
      .roles(Collections.singletonList(new EventProcessRoleRequestDto(new IdentityDto(userId, IdentityType.USER))))
      .build();
    validateEventSources(userId, eventProcessMappingDto);
    validateAutogenerationEventSourceScopes(eventProcessMappingDto);
    if (eventSources.stream().anyMatch(source -> source.getSourceType().equals(EventSourceType.EXTERNAL)) &&
      !configurationService.getEventBasedProcessConfiguration().getEventImport().isEnabled()) {
      throw new OptimizeValidationException(
        "Cannot autogenerate process models using external sources unless event import is enabled");
    }

    final AutogenerationProcessModelDto autogeneratedProcessModelDto =
      autogenerationProcessModelService.generateModelFromEventSources(eventSources);
    eventProcessMappingDto.setXml(autogeneratedProcessModelDto.getXml());
    eventProcessMappingDto.setMappings(autogeneratedProcessModelDto.getMappings());
    validateMappingsAndXmlCompatibility(eventProcessMappingDto);
    return eventProcessMappingWriter.createEventProcessMapping(eventProcessMappingDto);
  }

  public void updateEventProcessMapping(final String userId,
                                        final String eventProcessId,
                                        final EventProcessMappingRequestDto updateRequest) {
    final EventProcessMappingDto eventProcessMappingDto = EventProcessMappingDto.builder()
      .id(eventProcessId)
      .name(Optional.ofNullable(updateRequest.getName()).orElse(DEFAULT_PROCESS_NAME))
      .xml(updateRequest.getXml())
      .mappings(updateRequest.getMappings())
      .lastModifier(userId)
      .eventSources(updateRequest.getEventSources())
      .build();
    validateMappingsAndXmlCompatibility(eventProcessMappingDto);
    validateEventSources(userId, eventProcessMappingDto);
    eventProcessMappingWriter.updateEventProcessMapping(eventProcessMappingDto);
  }

  public ConflictResponseDto getDeleteConflictingItems(final String eventProcessId) {
    List<ReportDefinitionDto> reportsForProcessDefinitionKey =
      reportService.getAllReportsForProcessDefinitionKeyOmitXml(eventProcessId);
    Set<ConflictedItemDto> conflictedItems = new HashSet<>();
    for (ReportDefinitionDto reportForEventProcess : reportsForProcessDefinitionKey) {
      if (reportForEventProcess instanceof CombinedReportDefinitionRequestDto) {
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
        .collect(Collectors.toMap(
          EventProcessPublishStateDto::getProcessMappingId,
          Function.identity(),
          (mappingId1, mappingId2) -> mappingId2
        ));

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

    final IdResponseDto processPublishStateId =
      eventProcessPublishStateWriter.createEventProcessPublishState(processPublishState);
    eventProcessPublishStateWriter.deleteAllEventProcessPublishStatesForEventProcessMappingIdExceptOne(
      eventProcessMappingId,
      processPublishStateId.getId()
    );
  }

  private EventImportSourceDto createEventImportSourceFromDataSource(EventSourceEntryDto<?> eventSourceEntryDto) {
    Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> minAndMaxEventTimestamps;
    if (EventSourceType.EXTERNAL.equals(eventSourceEntryDto.getSourceType())) {
      minAndMaxEventTimestamps = externalEventService.getMinAndMaxIngestedTimestamps();
    } else if (EventSourceType.CAMUNDA.equals(eventSourceEntryDto.getSourceType())) {
      minAndMaxEventTimestamps =
        camundaEventService.getMinAndMaxIngestedTimestampsForDefinition(((CamundaEventSourceConfigDto) eventSourceEntryDto
          .getConfiguration()).getProcessDefinitionKey());
    } else {
      throw new OptimizeRuntimeException(String.format(
        "Cannot create import source from type: %s", eventSourceEntryDto.getSourceType()
      ));
    }
    return EventImportSourceDto.builder()
      .firstEventForSourceAtTimeOfPublishTimestamp(minAndMaxEventTimestamps.getLeft().orElse(getEpochMilliTimestamp()))
      .lastEventForSourceAtTimeOfPublishTimestamp(minAndMaxEventTimestamps.getRight().orElse(getEpochMilliTimestamp()))
      .lastImportedEventTimestamp(getEpochMilliTimestamp())
      .eventSource(eventSourceEntryDto)
      .build();
  }

  private OffsetDateTime getEpochMilliTimestamp() {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.systemDefault());
  }

  public void cancelPublish(final String userId, final String eventProcessMappingId) {
    final EventProcessMappingDto eventProcessMapping = getEventProcessMapping(userId, eventProcessMappingId);

    if (!PUBLISH_CANCELABLE_STATES.contains(eventProcessMapping.getState())) {
      throw new InvalidEventProcessStateException(
        "Cannot cancel publishing of event based process from state: " + eventProcessMapping.getState()
      );
    }

    final boolean publishWasCanceledSuccessfully = eventProcessPublishStateWriter
      .deleteAllEventProcessPublishStatesForEventProcessMappingId(eventProcessMappingId);

    if (!publishWasCanceledSuccessfully) {
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
    final List<EventSourceEntryDto<?>> eventSources = eventProcessMappingDto.getEventSources();
    if (eventSources == null || eventSources.contains(null)) {
      throw new OptimizeValidationException("Sources for an event based process cannot be null");
    }
    final List<CamundaEventSourceEntryDto> invalidTracingConfig = eventProcessMappingDto.getEventSources()
      .stream()
      .filter(CamundaEventSourceEntryDto.class::isInstance)
      .map(CamundaEventSourceEntryDto.class::cast)
      .filter(source -> !source.getConfiguration().isTracedByBusinessKey() &&
        StringUtils.isEmpty(source.getConfiguration().getTraceVariable()))
      .collect(toList());
    if (!invalidTracingConfig.isEmpty()) {
      throw new OptimizeValidationException(String.format(
        "The Camunda event sources with keys %s are traced by variable but do not have a variable name set.",
        invalidTracingConfig
      ));
    }
    validateAccessToCamundaEventSourcesOrFail(userId, eventProcessMappingDto);
    validateNoDuplicateCamundaEventSources(eventProcessMappingDto);
    validateNoEventProcessesAsEventSource(eventProcessMappingDto);
    validateCamundaEventSourcesAreImported(eventProcessMappingDto);
    validateCompatibleExternalEventSources(eventProcessMappingDto);
  }

  private void validateCamundaEventSourcesAreImported(final EventProcessMappingDto eventProcessMappingDto) {
    final Set<String> camundaEventIndexSuffixes =
      camundaActivityEventReader.getIndexSuffixesForCurrentActivityIndices();
    final List<String> unimportedEventCamundaSources = eventProcessMappingDto.getEventSources().stream()
      .filter(CamundaEventSourceEntryDto.class::isInstance)
      .map(CamundaEventSourceEntryDto.class::cast)
      .map(camundaSource -> camundaSource.getConfiguration().getProcessDefinitionKey().toLowerCase())
      .filter(definitionKey -> !camundaEventIndexSuffixes.contains(definitionKey))
      .collect(toList());
    if (!unimportedEventCamundaSources.isEmpty()) {
      throw new OptimizeValidationException(
        "The following process definition IDs cannot be used in Camunda event sources as no events have been imported" +
          " as event data: " + unimportedEventCamundaSources);
    }
  }

  private void validateAutogenerationEventSourceScopes(final EventProcessMappingDto eventProcessMappingDto) {
    eventProcessMappingDto.getEventSources()
      .forEach(eventSource -> {
        final List<EventScopeType> eventScope = eventSource.getConfiguration().getEventScope();
        if (eventScope.size() > 1) {
          throw new OptimizeValidationException("Event sources can only have a single event scope for autogeneration");
        }
        if (eventSource.getSourceType() == null) {
          throw new OptimizeValidationException("Event sources must have a specified type");
        }
        if (EventSourceType.EXTERNAL.equals(eventSource.getSourceType()) && !eventScope.contains(EventScopeType.ALL)) {
          throw new OptimizeValidationException(String.format(
            "An external event source must have an event scope of type %s for autogeneration",
            EventScopeType.ALL
          ));
        } else if (EventSourceType.CAMUNDA.equals(eventSource.getSourceType()) &&
          !(eventScope.contains(EventScopeType.PROCESS_INSTANCE) || eventScope.contains(EventScopeType.START_END))) {
          throw new OptimizeValidationException(String.format(
            "A Camunda event source must have an event scope of either type %s or %s for autogeneration",
            EventScopeType.PROCESS_INSTANCE, EventScopeType.START_END
          ));
        }
      });

  }

  private void validateNoEventProcessesAsEventSource(final EventProcessMappingDto eventProcessMappingDto) {
    List<String> eventProcessSourceKeys = eventProcessMappingDto.getEventSources()
      .stream()
      .filter(eventSource -> EventSourceType.CAMUNDA.equals(eventSource.getSourceType()))
      .map(source -> ((CamundaEventSourceConfigDto) source.getConfiguration()).getProcessDefinitionKey())
      .filter(key -> eventProcessDefinitionService.getEventProcessDefinitionByKey(key).isPresent())
      .collect(toList());
    if (!eventProcessSourceKeys.isEmpty()) {
      throw new OptimizeConflictException(String.format(
        "Event sources with keys %s are not permitted as they are event processes themselves",
        eventProcessSourceKeys
      ));
    }
  }

  private boolean validateEventSourceAuthorisation(final String userId, final EventSourceEntryDto<?> eventSource) {
    return EventSourceType.EXTERNAL.equals(eventSource.getSourceType())
      || definitionAuthorizationService.isAuthorizedToSeeProcessDefinition(
      userId,
      IdentityType.USER,
      ((CamundaEventSourceConfigDto) eventSource.getConfiguration()).getProcessDefinitionKey(),
      ((CamundaEventSourceEntryDto) eventSource).getConfiguration().getTenants()
    );
  }

  private void validateAccessToCamundaEventSourcesOrFail(final String userId,
                                                         final EventProcessMappingDto eventProcessMappingDto) {
    final Set<String> notAuthorizedProcesses = eventProcessMappingDto.getEventSources().stream()
      .filter(eventSource -> !validateEventSourceAuthorisation(userId, eventSource))
      .map(source -> ((CamundaEventSourceConfigDto) source.getConfiguration()).getProcessDefinitionKey())
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
      .filter(eventSourceEntryDto -> EventSourceType.CAMUNDA.equals(eventSourceEntryDto.getSourceType()))
      .map(source -> ((CamundaEventSourceConfigDto) source.getConfiguration()).getProcessDefinitionKey())
      .filter(Objects::nonNull)
      .filter(key -> !processDefinitionKeys.add(key))
      .collect(toSet());
    if (!duplicates.isEmpty()) {
      final String errorMessage = String.format(
        "Only one event source for each process definition can exist for an Event Process Mapping. " +
          "Mapping contains duplicates for process definition keys %s", duplicates);
      throw new OptimizeConflictException(errorMessage);
    }
  }

  private void validateCompatibleExternalEventSources(final EventProcessMappingDto eventProcessMappingDto) {
    final Map<Boolean, List<ExternalEventSourceEntryDto>> externalSourcesByIncludeAllGroups =
      eventProcessMappingDto.getEventSources().stream()
        .filter(ExternalEventSourceEntryDto.class::isInstance)
        .map(ExternalEventSourceEntryDto.class::cast)
        .collect(Collectors.groupingBy(source -> source.getConfiguration().isIncludeAllGroups()));
    final List<ExternalEventSourceEntryDto> sourcesForAllGroups =
      externalSourcesByIncludeAllGroups.getOrDefault(true, Collections.emptyList());
    if (sourcesForAllGroups.size() > 1) {
      throw new OptimizeValidationException(
        "Only one external event source with all groups selected can be used for event mappings");
    }
    if (sourcesForAllGroups.stream().anyMatch(source -> source.getConfiguration().getGroup() != null)) {
      throw new OptimizeValidationException("An external event source for all groups cannot specify a group name");
    }
    final List<ExternalEventSourceEntryDto> sourcesForSpecificGroups =
      externalSourcesByIncludeAllGroups.getOrDefault(false, Collections.emptyList());
    if (!sourcesForAllGroups.isEmpty() && !sourcesForSpecificGroups.isEmpty()) {
      throw new OptimizeValidationException(String.format(
        "External event sources for a specified group cannot be selected if all groups are also " +
          "included as an event source. Individual groups selected: %s", sourcesForSpecificGroups));
    }
    final List<String> duplicateGroups = sourcesForSpecificGroups.stream()
      .map(source -> source.getConfiguration().getGroup())
      .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
      .entrySet()
      .stream()
      .filter(countPerGroup -> countPerGroup.getValue() > 1)
      .map(Map.Entry::getKey)
      .collect(toList());
    if (!duplicateGroups.isEmpty()) {
      throw new OptimizeValidationException(String.format(
        "The following group names were supplied for more than one external event source: %s",
        sourcesForSpecificGroups
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

  private void validateMappingsAndXmlCompatibility(final EventProcessMappingDto eventProcessMappingDto) {
    final Optional<BpmnModelInstance> modelInstance = Optional.ofNullable(eventProcessMappingDto.getXml())
      .map(this::parseXmlIntoBpmnModel);
    Set<String> flowNodeIds = modelInstance.map(instance -> extractFlowNodeNames(instance).keySet())
      .orElse(Collections.emptySet());

    Map<String, EventMappingDto> eventMappings = eventProcessMappingDto.getMappings();
    if (eventMappings != null) {
      if (!flowNodeIds.containsAll(eventMappings.keySet())) {
        throw new BadRequestException("All Flow Node IDs for event mappings must exist within the provided XML");
      }
      if (eventMappings.entrySet().stream()
        .anyMatch(mapping -> mapping.getValue().getStart() == null && mapping.getValue().getEnd() == null)) {
        throw new BadRequestException("All Flow Node mappings provided must have a start and/or end event mapped");
      }
      final List<String> singleMappableEventIdsInModel = modelInstance.map(this::getSingleMappableEventIds)
        .orElse(Collections.emptyList());

      if (eventMappings.entrySet().stream().anyMatch(mapping -> singleMappableEventIdsInModel.contains(mapping.getKey())
        && mapping.getValue().getStart() != null && mapping.getValue().getEnd() != null)) {
        throw new BadRequestException("BPMN events must have only one of either a start and end mapping");
      }
    }
  }

  private List<String> getSingleMappableEventIds(final BpmnModelInstance model) {
    return model.getModelElementsByType(Event.class)
      .stream()
      .map(BaseElement::getId)
      .collect(toList());
  }

  private BpmnModelInstance parseXmlIntoBpmnModel(final String xmlString) {
    try {
      return BpmnModelUtil.parseBpmnModel(xmlString);
    } catch (ModelParseException ex) {
      throw new BadRequestException("The provided xml is not valid");
    }
  }

}
