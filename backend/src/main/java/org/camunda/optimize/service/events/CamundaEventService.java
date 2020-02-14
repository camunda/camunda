/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CamundaEventService implements EventFetcherService {
  public static final String EVENT_GROUP_CAMUNDA = "camunda";

  private final CamundaActivityEventReader camundaActivityEventReader;
  private final String definitionKey;

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long eventTimestamp, final int limit) {
    return camundaActivityEventReader.getCamundaActivityEventsForDefinitionAfter(definitionKey, eventTimestamp, limit)
      .stream()
      .map(this::mapToEventDto)
      .collect(Collectors.toList());
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long eventTimestamp) {
    return camundaActivityEventReader.getCamundaActivityEventsForDefinitionAt(definitionKey, eventTimestamp)
      .stream()
      .map(this::mapToEventDto)
      .collect(Collectors.toList());
  }

  private EventDto mapToEventDto(final CamundaActivityEventDto camundaActivityEventDto) {
    return EventDto.builder()
      .id(camundaActivityEventDto.getActivityInstanceId())
      .eventName(camundaActivityEventDto.getActivityId())
      .traceId(camundaActivityEventDto.getProcessInstanceId())
      .timestamp(camundaActivityEventDto.getTimestamp().toInstant().toEpochMilli())
      .ingestionTimestamp(camundaActivityEventDto.getTimestamp().toInstant().toEpochMilli())
      .group(camundaActivityEventDto.getProcessDefinitionKey())
      .source(EVENT_GROUP_CAMUNDA)
      .build();
  }
}
