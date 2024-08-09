/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.events.autogeneration;

import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatableExternalEventsTraceDto;
import io.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatableInstanceDto;
import io.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatedInstanceDto;
import io.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatedTraceDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import io.camunda.optimize.service.db.events.EventTraceStateServiceFactory;
import io.camunda.optimize.service.events.EventTraceStateService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
public class CorrelatedInstanceService {

  private final EventTraceStateService externalEventTraceStateService;

  public CorrelatedInstanceService(
      final EventTraceStateServiceFactory eventTraceStateServiceFactory) {
    externalEventTraceStateService =
        eventTraceStateServiceFactory.createEventTraceStateService(EXTERNAL_EVENTS_INDEX_SUFFIX);
  }

  public List<CorrelatedTraceDto> getCorrelatedTracesForEventSources(
      final List<EventSourceEntryDto<?>> eventSources, final List<String> correlationValues) {
    final Map<EventSourceType, List<EventSourceEntryDto>> sourceByType =
        eventSources.stream().collect(groupingBy(EventSourceEntryDto::getSourceType));
    final Map<String, EventSourceEntryDto<?>> sourcesBySourceIdentifier =
        eventSources.stream()
            .collect(toMap(EventSourceEntryDto::getSourceIdentifier, Function.identity()));

    final List<CorrelatableInstanceDto> correlatableInstances = new ArrayList<>();
    if (!CollectionUtils.isEmpty(sourceByType.get(EventSourceType.EXTERNAL))) {
      correlatableInstances.addAll(
          externalEventTraceStateService.getTracesWithTraceIdIn(correlationValues).stream()
              .filter(trace -> !trace.getEventTrace().isEmpty())
              .map(CorrelatableExternalEventsTraceDto::fromEventTraceState)
              .collect(Collectors.toList()));
    }

    return correlatableInstances.stream()
        .collect(
            toMap(
                instance -> getCorrelationValueForInstance(instance, sourcesBySourceIdentifier),
                Arrays::asList,
                (instance1, instance2) ->
                    Stream.concat(instance1.stream(), instance2.stream())
                        .sorted(Comparator.comparing(CorrelatableInstanceDto::getStartDate))
                        .collect(Collectors.toList())))
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey() != null)
        .map(
            correlatedTrace ->
                CorrelatedTraceDto.builder()
                    .correlationValue(correlatedTrace.getKey())
                    .instances(convertToCorrelatedInstances(correlatedTrace))
                    .build())
        .collect(Collectors.toList());
  }

  private List<CorrelatedInstanceDto> convertToCorrelatedInstances(
      final Map.Entry<String, List<CorrelatableInstanceDto>> correlatedTrace) {
    return correlatedTrace.getValue().stream()
        .map(
            correlatable ->
                CorrelatedInstanceDto.builder()
                    .sourceIdentifier(correlatable.getSourceIdentifier())
                    .startDate(correlatable.getStartDate())
                    .build())
        .collect(Collectors.toList());
  }

  private String getCorrelationValueForInstance(
      final CorrelatableInstanceDto correlatableInstance,
      final Map<String, EventSourceEntryDto<?>> sourcesByIdentifier) {
    return correlatableInstance.getCorrelationValueForEventSource(
        sourcesByIdentifier.get(correlatableInstance.getSourceIdentifier()));
  }
}
