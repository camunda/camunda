/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service.events.autogeneration;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatableProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatedInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatedTraceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.service.es.reader.CorrelatedProcessInstanceReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@Component
@RequiredArgsConstructor
public class CorrelatedInstanceService {

  private final CorrelatedProcessInstanceReader correlatedProcessInstanceReader;

  public List<String> getCorrelationValueSampleForEventSources(final List<EventSourceEntryDto> eventSources) {
    return correlatedProcessInstanceReader.getCorrelationValueSampleForEventSources(eventSources);
  }

  public List<CorrelatedTraceDto> getCorrelatedTracesForEventSources(final List<EventSourceEntryDto> eventSources,
                                                                     final List<String> correlationValues) {
    final Map<EventSourceType, List<EventSourceEntryDto>> sourceByType = eventSources.stream()
      .collect(groupingBy(EventSourceEntryDto::getType));
    List<CorrelatedTraceDto> correlatedTraceDtos = new ArrayList<>();
    if (!CollectionUtils.isEmpty(sourceByType.get(EventSourceType.CAMUNDA))) {
      final List<EventSourceEntryDto> camundaSources = eventSources.stream()
        .filter(eventSource -> EventSourceType.CAMUNDA.equals(eventSource.getType()))
        .collect(Collectors.toList());

      final List<CorrelatableProcessInstanceDto> correlatableInstances =
        correlatedProcessInstanceReader.getCorrelatableInstancesForCamundaSources(
          sourceByType.get(EventSourceType.CAMUNDA),
          correlationValues
        );

      final Map<String, EventSourceEntryDto> defKeyToSourceMap = camundaSources.stream()
        .collect(toMap(EventSourceEntryDto::getProcessDefinitionKey, Function.identity()));
      correlatedTraceDtos = correlatableInstances.stream()
        .collect(toMap(
          instance -> getCorrelationValueForInstance(instance, defKeyToSourceMap),
          Arrays::asList,
          (instance1, instance2) -> Stream.concat(instance1.stream(), instance2.stream())
            .sorted(Comparator.comparing(CorrelatableProcessInstanceDto::getStartDate))
            .collect(Collectors.toList())
        ))
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey() != null)
        .map(correlatedTrace -> CorrelatedTraceDto.builder()
          .correlationValue(correlatedTrace.getKey())
          .instances(convertToCorrelatedInstances(correlatedTrace))
          .build())
        .collect(Collectors.toList());
    }
    if (!CollectionUtils.isEmpty(sourceByType.get(EventSourceType.EXTERNAL))) {
      // TODO OPT-3861
    }
    return correlatedTraceDtos;
  }

  private List<CorrelatedInstanceDto> convertToCorrelatedInstances(
    final Map.Entry<String, List<CorrelatableProcessInstanceDto>> correlatedTrace) {
    return correlatedTrace.getValue()
      .stream()
      .map(correlatable -> CorrelatedInstanceDto.builder()
        .processDefinitionKey(correlatable.getProcessDefinitionKey())
        .startDate(correlatable.getStartDate())
        .build())
      .collect(Collectors.toList());
  }

  private String getCorrelationValueForInstance(final CorrelatableProcessInstanceDto instance,
                                                final Map<String, EventSourceEntryDto> defKeyToSourceMap) {
    final EventSourceEntryDto sourceForInstance = defKeyToSourceMap.get(instance.getProcessDefinitionKey());
    if (sourceForInstance.isTracedByBusinessKey()) {
      return instance.getBusinessKey();
    } else {
      final String traceVariableName = sourceForInstance.getTraceVariable();
      return instance.getVariables()
        .stream()
        .filter(var -> var.getName().equals(traceVariableName))
        .map(SimpleProcessVariableDto::getValue)
        .findFirst().orElse(null);
    }
  }

}
