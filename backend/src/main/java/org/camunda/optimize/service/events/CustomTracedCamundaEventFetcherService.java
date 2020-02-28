/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.service.es.reader.BusinessKeyReader;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.reader.TimestampBasedImportIndexReader;
import org.camunda.optimize.service.es.reader.VariableUpdateInstanceReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.DefinitionVersionHandlingUtil;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.service.importing.engine.handler.CompletedProcessInstanceImportIndexHandler.COMPLETED_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID;
import static org.camunda.optimize.service.importing.engine.handler.RunningProcessInstanceImportIndexHandler.RUNNING_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID;
import static org.camunda.optimize.service.importing.engine.handler.VariableUpdateInstanceImportIndexHandler.VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID;

@AllArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class CustomTracedCamundaEventFetcherService implements EventFetcherService {

  public static final String EVENT_SOURCE_CAMUNDA = "camunda";

  private final String definitionKey;
  private final EventSourceEntryDto eventSource;
  private final DateFormat dateTimeFormatter;

  private final CamundaActivityEventReader camundaActivityEventReader;
  private final ProcessDefinitionReader processDefinitionReader;
  private final VariableUpdateInstanceReader variableUpdateInstanceReader;
  private final BusinessKeyReader businessKeyReader;
  private final TimestampBasedImportIndexReader timestampBasedImportIndexReader;

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long eventTimestamp, final int limit) {
    final List<EventDto> uncorrelatedEvents = camundaActivityEventReader
      .getCamundaActivityEventsForDefinitionWithVersionAndTenantBetween(
        definitionKey,
        getVersionsForEventRetrieval(),
        eventSource.getTenants(),
        eventTimestamp,
        getMaxTimestampForEventRetrieval(),
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

    final Map<String, List<VariableUpdateInstanceDto>> processInstanceToVariableUpdates =
      variableUpdateInstanceReader.getVariableInstanceUpdatesForProcessInstanceIds(processInstanceIds)
        .stream()
        .distinct()
        .collect(groupingBy(VariableUpdateInstanceDto::getProcessInstanceId));

    eventDtosToImport.forEach(
      eventDto -> eventDto.setData(extractVariablesDataForEvent(processInstanceToVariableUpdates.get(eventDto.getTraceId()))));

    List<EventDto> correlatedEvents;
    if (eventSource.getTracedByBusinessKey()) {
      Map<String, String> instanceIdToBusinessKeys =
        businessKeyReader.getBusinessKeysForProcessInstanceIds(processInstanceIds)
          .stream()
          .filter(businessKeyDto -> businessKeyDto.getBusinessKey() != null)
          .collect(Collectors.toMap(
            BusinessKeyDto::getProcessInstanceId,
            BusinessKeyDto::getBusinessKey
          ));
      correlatedEvents = eventDtosToImport.stream()
        .filter(eventDto -> instanceIdToBusinessKeys.get(eventDto.getTraceId()) != null)
        .peek(eventDto -> eventDto.setTraceId(instanceIdToBusinessKeys.get(eventDto.getTraceId())))
        .collect(Collectors.toList());
    } else {
      correlatedEvents = eventDtosToImport.stream()
        .filter(eventDto -> processInstanceToVariableUpdates.get(eventDto.getTraceId()) != null)
        .peek(eventDto -> {
          // if the value of the correlation key changes during the running of the instance, we take the original value
          VariableUpdateInstanceDto firstUpdate = processInstanceToVariableUpdates.get(eventDto.getTraceId())
            .stream()
            .filter(variableUpdateInstanceDto -> variableUpdateInstanceDto.getName()
              .equalsIgnoreCase(eventSource.getTraceVariable()))
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

  private Object extractVariablesDataForEvent(List<VariableUpdateInstanceDto> variableUpdateInstanceDtos) {
    if (variableUpdateInstanceDtos == null || variableUpdateInstanceDtos.isEmpty()) {
      return null;
    }
    Map<String, Object> latestVariableUpdatesById =
      variableUpdateInstanceDtos.stream().collect(groupingBy(VariableUpdateInstanceDto::getName))
        .entrySet().stream()
        .peek(entry -> entry.getValue().sort(Comparator.comparing(VariableUpdateInstanceDto::getTimestamp).reversed()))
        .collect(Collectors.toMap(
          Map.Entry::getKey,
          entry -> getTypedVariable(entry.getValue().get(0))
        ));
    return latestVariableUpdatesById;
  }

  private Object getTypedVariable(final VariableUpdateInstanceDto variableUpdateInstanceDto) {
    String type = variableUpdateInstanceDto.getType();
    String value = variableUpdateInstanceDto.getValue();
    try {
      if (type.equalsIgnoreCase(VariableType.STRING.getId())) {
        return value;
      } else if (type.equalsIgnoreCase(VariableType.INTEGER.getId())) {
        return Integer.parseInt(value);
      } else if (type.equalsIgnoreCase(VariableType.DOUBLE.getId())) {
        return Double.valueOf(value);
      } else if (type.equalsIgnoreCase(VariableType.SHORT.getId())) {
        return Short.parseShort(value);
      } else if (type.equalsIgnoreCase(VariableType.BOOLEAN.getId())) {
        return Boolean.parseBoolean(value);
      } else if (type.equalsIgnoreCase(VariableType.LONG.getId())) {
        return Long.valueOf(value);
      } else if (type.equalsIgnoreCase(VariableType.DATE.getId())) {
        return dateTimeFormatter.parse(value);
      }
    } catch (ParseException | NumberFormatException ex) {
      log.warn(
        "Could not parse variable value {} with type {} into supported type, will use String as type", value, type,
        ex
      );
    }
    return value;
  }

  private long getMaxTimestampForEventRetrieval() {
    return timestampBasedImportIndexReader.getAllImportIndicesForTypes(getImportIndicesToSearch())
      .stream()
      .filter(importIndex -> Objects.nonNull(importIndex.getLastImportExecutionTimestamp()))
      .min(Comparator.comparing(TimestampBasedImportIndexDto::getLastImportExecutionTimestamp))
      .map(importIndex -> {
        log.debug(
          "Searching using the max timestamp {} from import index type {}",
          importIndex.getLastImportExecutionTimestamp(),
          importIndex.getEsTypeIndexRefersTo()
        );
        return importIndex.getLastImportExecutionTimestamp().toInstant().toEpochMilli();
      })
      .orElseThrow(() -> new OptimizeRuntimeException("Could not find the maximum timestamp to search for"));
  }

  private List<String> getImportIndicesToSearch() {
    if (!eventSource.getTracedByBusinessKey()) {
      return Arrays.asList(
        COMPLETED_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID,
        RUNNING_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID,
        VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID
      );
    } else {
      return Arrays.asList(
        COMPLETED_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID,
        RUNNING_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID
      );
    }
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
