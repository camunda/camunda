/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.eventprocess.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.CancelableEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.reader.BusinessKeyReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.reader.VariableUpdateInstanceReader;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.DefinitionVersionHandlingUtil;

import java.text.DateFormat;
import java.text.ParseException;
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

@AllArgsConstructor
@Slf4j
public class CustomTracedEventProcessInstanceImportService implements ImportService<CamundaActivityEventDto> {

  private static final String EVENT_SOURCE_CAMUNDA = "camunda";

  private final CamundaEventSourceConfigDto eventSourceConfig;

  private final DateFormat variableDateTimeFormatter;
  private final EventProcessInstanceImportService eventProcessInstanceImportService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final VariableUpdateInstanceReader variableUpdateInstanceReader;
  private final BusinessKeyReader businessKeyReader;

  @Override
  public void executeImport(final List<CamundaActivityEventDto> camundaActivities,
                            final Runnable importCompleteCallback) {
    final List<EventDto> filteredEvents = filterForConfiguredTenantsAndVersions(camundaActivities)
      .stream()
      .map(this::mapToEventDto)
      .collect(Collectors.toList());

    final List<EventDto> correlatedEvents = correlateCamundaEvents(filteredEvents);
    eventProcessInstanceImportService.executeImport(correlatedEvents, importCompleteCallback);
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return eventProcessInstanceImportService.getElasticsearchImportJobExecutor();
  }

  private List<CamundaActivityEventDto> filterForConfiguredTenantsAndVersions(final List<CamundaActivityEventDto> camundaActivities) {
    List<CamundaActivityEventDto> filteredActivities = camundaActivities.stream()
      .filter(activity -> eventSourceConfig.getTenants().contains(activity.getTenantId()))
      .collect(Collectors.toList());
    final List<String> versionsInSource = getVersionsToIncludeForFilter();
    if (!versionsInSource.contains(ALL_VERSIONS)) {
      return filteredActivities.stream()
        .filter(activity -> versionsInSource.contains(activity.getProcessDefinitionVersion()))
        .collect(Collectors.toList());
    }
    return filteredActivities;
  }

  private List<EventDto> correlateCamundaEvents(final List<EventDto> eventDtosToImport) {
    log.trace("Correlating [{}] camunda activity events for process definition key {}.",
              eventDtosToImport.size(), eventSourceConfig.getProcessDefinitionKey()
    );
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
    if (eventSourceConfig.isTracedByBusinessKey()) {
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
        .filter(eventDto -> {
          final List<VariableUpdateInstanceDto> variablesForTraceId =
            processInstanceToVariableUpdates.get(eventDto.getTraceId());
          return variablesForTraceId != null && variablesForTraceId.stream()
            .anyMatch(var -> var.getName().equalsIgnoreCase(eventSourceConfig.getTraceVariable()));
        })
        .peek(eventDto -> {
          // if the value of the correlation key changes during the running of the instance, we take the original value
          VariableUpdateInstanceDto firstUpdate = processInstanceToVariableUpdates.get(eventDto.getTraceId())
            .stream()
            .filter(variableUpdateInstanceDto -> variableUpdateInstanceDto.getName()
              .equalsIgnoreCase(eventSourceConfig.getTraceVariable()))
            .distinct()
            .min(Comparator.comparing(VariableUpdateInstanceDto::getTimestamp)).get();
          // If there are multiple values in this variable, we always take the first
          eventDto.setTraceId(firstUpdate.getValue().stream().sorted().findFirst().orElse(null));
          if (firstUpdate.getValue().size() > 1) {
            log.info("Variable {} has multiple values, setting traceID to first value in list.", firstUpdate.getName());
          }
        })
        .collect(Collectors.toList());
    }

    if (eventDtosToImport.size() > correlatedEvents.size()) {
      log.warn(
        "could not find the correlation key for some events in batch for event source config {}",
        eventSourceConfig
      );
    }
    return correlatedEvents;
  }

  private Object extractVariablesDataForEvent(List<VariableUpdateInstanceDto> variableUpdateInstanceDtos) {
    if (variableUpdateInstanceDtos == null || variableUpdateInstanceDtos.isEmpty()) {
      return null;
    }
    return variableUpdateInstanceDtos.stream().collect(groupingBy(VariableUpdateInstanceDto::getName))
      .entrySet().stream()
      .peek(entry -> entry.getValue().sort(Comparator.comparing(VariableUpdateInstanceDto::getTimestamp).reversed()))
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        entry -> getTypedVariable(entry.getValue().get(0))
      ));
  }

  private Object getTypedVariable(final VariableUpdateInstanceDto variableUpdateInstanceDto) {
    String type = variableUpdateInstanceDto.getType();
    String value = variableUpdateInstanceDto.getValue().stream().sorted().findFirst().orElse(null);
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
        return variableDateTimeFormatter.parse(value);
      }
    } catch (ParseException | NumberFormatException ex) {
      log.warn(
        "Could not parse variable value {} with type {} into supported type, will use String as type", value, type,
        ex
      );
    }
    return value;
  }

  private List<String> getVersionsToIncludeForFilter() {
    List<String> versionsForFilter = new ArrayList<>(eventSourceConfig.getVersions());
    versionsForFilter.removeIf(Objects::isNull);
    if (DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest(versionsForFilter)) {
      return Collections.singletonList(
        processDefinitionReader.getLatestVersionToKey(eventSourceConfig.getProcessDefinitionKey()));
    } else if (DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll(versionsForFilter)) {
      return Collections.singletonList(ALL_VERSIONS);
    }
    return versionsForFilter;
  }

  private CancelableEventDto mapToEventDto(final CamundaActivityEventDto camundaActivityEventDto) {
    return CancelableEventDto.builder()
      .id(camundaActivityEventDto.getActivityInstanceId())
      .eventName(camundaActivityEventDto.getActivityId())
      .traceId(camundaActivityEventDto.getProcessInstanceId())
      .timestamp(camundaActivityEventDto.getTimestamp().toInstant().toEpochMilli())
      .ingestionTimestamp(camundaActivityEventDto.getTimestamp().toInstant().toEpochMilli())
      .group(camundaActivityEventDto.getProcessDefinitionKey())
      .source(EVENT_SOURCE_CAMUNDA)
      .canceled(camundaActivityEventDto.isCanceled())
      .build();
  }

}
