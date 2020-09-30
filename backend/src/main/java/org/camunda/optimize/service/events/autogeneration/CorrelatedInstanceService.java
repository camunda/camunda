/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service.events.autogeneration;

import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.query.event.process.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatableExternalEventsTraceDto;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatableInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatedInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatedTraceDto;
import org.camunda.optimize.service.EventTraceStateService;
import org.camunda.optimize.service.EventTraceStateServiceFactory;
import org.camunda.optimize.service.es.reader.CorrelatedCamundaProcessInstanceReader;
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
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

@Component
public class CorrelatedInstanceService {

  private final CorrelatedCamundaProcessInstanceReader correlatedCamundaProcessInstanceReader;
  private final EventTraceStateService externalEventTraceStateService;

  public CorrelatedInstanceService(CorrelatedCamundaProcessInstanceReader correlatedCamundaProcessInstanceReader,
                                   EventTraceStateServiceFactory eventTraceStateServiceFactory) {
    this.correlatedCamundaProcessInstanceReader = correlatedCamundaProcessInstanceReader;
    this.externalEventTraceStateService =
      eventTraceStateServiceFactory.createEventTraceStateService(EXTERNAL_EVENTS_INDEX_SUFFIX);
  }

  public List<String> getCorrelationValueSampleForCamundaEventSources(final List<EventSourceEntryDto> eventSources) {
    return correlatedCamundaProcessInstanceReader.getCorrelationValueSampleForEventSources(eventSources);
  }

  public List<CorrelatedTraceDto> getCorrelatedTracesForEventSources(final List<EventSourceEntryDto> eventSources,
                                                                     final List<String> correlationValues) {
    final Map<EventSourceType, List<EventSourceEntryDto>> sourceByType = eventSources.stream()
      .collect(groupingBy(EventSourceEntryDto::getType));
    final List<CorrelatableInstanceDto> correlatableInstances = new ArrayList<>();
    if (!CollectionUtils.isEmpty(sourceByType.get(EventSourceType.CAMUNDA))) {
      correlatableInstances.addAll(
        correlatedCamundaProcessInstanceReader.getCorrelatableInstancesForSources(
          sourceByType.get(EventSourceType.CAMUNDA),
          correlationValues
        ));
    }
    if (!CollectionUtils.isEmpty(sourceByType.get(EventSourceType.EXTERNAL))) {
      correlatableInstances.addAll(
        externalEventTraceStateService.getTracesWithTraceIdIn(correlationValues)
          .stream()
          .filter(trace -> !trace.getEventTrace().isEmpty())
          .map(CorrelatableExternalEventsTraceDto::fromEventTraceState)
          .collect(Collectors.toList()));
    }

    final Map<String, EventSourceEntryDto> camundaSourceByIdentifier = sourceByType.get(EventSourceType.CAMUNDA)
      .stream()
      .collect(toMap(EventSourceEntryDto::getSourceIdentifier, Function.identity()));
    return correlatableInstances.stream()
      .collect(toMap(
        instance -> getCorrelationValueForInstance(instance, camundaSourceByIdentifier),
        Arrays::asList,
        (instance1, instance2) -> Stream.concat(instance1.stream(), instance2.stream())
          .sorted(Comparator.comparing(CorrelatableInstanceDto::getStartDate))
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

  private List<CorrelatedInstanceDto> convertToCorrelatedInstances(
    final Map.Entry<String, List<CorrelatableInstanceDto>> correlatedTrace) {
    return correlatedTrace.getValue()
      .stream()
      .map(correlatable -> CorrelatedInstanceDto.builder()
        .sourceIdentifier(correlatable.getSourceIdentifier())
        .startDate(correlatable.getStartDate())
        .build())
      .collect(Collectors.toList());
  }

  private String getCorrelationValueForInstance(final CorrelatableInstanceDto correlatableInstance,
                                                final Map<String, EventSourceEntryDto> sourceByIdentifier) {
    final EventSourceEntryDto sourceForInstance = sourceByIdentifier.get(correlatableInstance.getSourceIdentifier());
    return correlatableInstance.getCorrelationValueForEventSource(sourceForInstance);
  }

}
