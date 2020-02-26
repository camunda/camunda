/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.service.es.reader.BusinessKeyReader;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.reader.VariableUpdateInstanceReader;
import org.camunda.optimize.service.util.DefinitionVersionHandlingUtil;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;

@AllArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class CustomTracedCamundaEventFetcherService implements EventFetcherService {

  public static final String EVENT_SOURCE_CAMUNDA = "camunda";

  private final String definitionKey;
  private final EventSourceEntryDto eventSource;

  private final CamundaActivityEventReader camundaActivityEventReader;
  private final ProcessDefinitionReader processDefinitionReader;
  private final VariableUpdateInstanceReader variableUpdateInstanceReader;
  private final BusinessKeyReader businessKeyReader;

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long eventTimestamp, final int limit) {
    final List<EventDto> uncorrelatedEvents = camundaActivityEventReader
      .getCamundaActivityEventsForDefinitionWithVersionAndTenantAfter(
        definitionKey,
        getVersionsForEventRetrieval(),
        eventSource.getTenants(),
        eventTimestamp,
        limit
      )
      .stream()
      .map(this::mapToEventDto)
      .collect(Collectors.toList());
    return correlateCamundaEvents(uncorrelatedEvents);
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long eventTimestamp) {
    final List<EventDto> uncorrelatedEvents = camundaActivityEventReader
      .getCamundaActivityEventsForDefinitionWithVersionAndTenantAt(
        definitionKey,
        getVersionsForEventRetrieval(),
        eventSource.getTenants(),
        eventTimestamp
      )
      .stream()
      .map(this::mapToEventDto)
      .collect(Collectors.toList());
    return correlateCamundaEvents(uncorrelatedEvents);
  }

  private List<String> getVersionsForEventRetrieval() {
    List<String> versionsForFilter = new ArrayList<>(eventSource.getVersions());
    versionsForFilter.removeIf(Objects::isNull);
    if (DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest(versionsForFilter)) {
      return Collections.singletonList(processDefinitionReader.getLatestVersionToKey(definitionKey));
    } else if (DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll(versionsForFilter)) {
      return Collections.singletonList(ALL_VERSIONS);
    }
    return versionsForFilter;
  }

  private List<EventDto> correlateCamundaEvents(final List<EventDto> eventDtosToImport) {
    Set<String> processInstanceIds = eventDtosToImport.stream()
      .map(EventDto::getTraceId)
      .collect(Collectors.toSet());

    List<EventDto> correlatedEvents;
    if (eventSource.getTracedByBusinessKey()) {
      Map<String, String> instanceIdToBusinessKeys =
        businessKeyReader.getBusinessKeysForProcessInstanceIds(processInstanceIds)
          .stream()
          .collect(Collectors.toMap(
            BusinessKeyDto::getProcessInstanceId,
            BusinessKeyDto::getBusinessKey
          ));
      correlatedEvents = eventDtosToImport.stream()
        .filter(eventDto -> instanceIdToBusinessKeys.get(eventDto.getTraceId()) != null)
        .peek(eventDto -> eventDto.setTraceId(instanceIdToBusinessKeys.get(eventDto.getTraceId())))
        .collect(Collectors.toList());
    } else {
      final Map<String, List<VariableUpdateInstanceDto>> processInstanceToVariableUpdates =
        variableUpdateInstanceReader.getVariableInstanceUpdatesForProcessInstanceIds(processInstanceIds)
          .stream()
          .filter(variableUpdateInstanceDto -> variableUpdateInstanceDto.getName()
            .equalsIgnoreCase(eventSource.getTraceVariable()))
          .distinct()
          .collect(groupingBy(VariableUpdateInstanceDto::getProcessInstanceId));
      correlatedEvents = eventDtosToImport.stream()
        .filter(eventDto -> processInstanceToVariableUpdates.get(eventDto.getTraceId()) != null)
        .peek(eventDto -> {
          // if the value of the correlation key changes during the running of the instance, we take the original value
          VariableUpdateInstanceDto firstUpdate = processInstanceToVariableUpdates.get(eventDto.getTraceId())
            .stream()
            .distinct()
            .min(Comparator.comparing(VariableUpdateInstanceDto::getTimestamp)).get();
          eventDto.setTraceId(firstUpdate.getValue());
        })
        .collect(Collectors.toList());
    }

    if (eventDtosToImport.size() > correlatedEvents.size()) {
      log.warn("could not find the correlation key for some events in batch for event source {}", eventSource);
    }
    return correlatedEvents;
  }

  private EventDto mapToEventDto(final CamundaActivityEventDto camundaActivityEventDto) {
    return EventDto.builder()
      .id(camundaActivityEventDto.getActivityInstanceId())
      .eventName(camundaActivityEventDto.getActivityId())
      .traceId(camundaActivityEventDto.getProcessInstanceId())
      .timestamp(camundaActivityEventDto.getTimestamp().toInstant().toEpochMilli())
      .ingestionTimestamp(camundaActivityEventDto.getTimestamp().toInstant().toEpochMilli())
      .group(camundaActivityEventDto.getProcessDefinitionKey())
      .source(EVENT_SOURCE_CAMUNDA)
      .build();
  }
}
