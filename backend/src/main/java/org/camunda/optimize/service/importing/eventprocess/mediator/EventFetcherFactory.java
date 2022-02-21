/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess.mediator;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceConfigDto;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.es.reader.ExternalEventReader;
import org.camunda.optimize.service.es.reader.importindex.TimestampBasedImportIndexReader;
import org.camunda.optimize.service.events.CamundaActivityEventFetcherService;
import org.camunda.optimize.service.events.EventFetcherService;
import org.camunda.optimize.service.events.ExternalEventByGroupsFetcherService;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class EventFetcherFactory {

  private final ExternalEventService externalEventService;

  private final CamundaActivityEventReader camundaActivityEventReader;
  private final ExternalEventReader externalEventReader;
  private final TimestampBasedImportIndexReader timestampBasedImportIndexReader;

  public EventFetcherService<?> createEventFetcherForEventImportSource(EventImportSourceDto eventImportSourceDto) {
    if (EventSourceType.EXTERNAL.equals(eventImportSourceDto.getEventImportSourceType())) {
      final boolean includeAllGroups = eventImportSourceDto.getEventSourceConfigurations().stream()
        .map(ExternalEventSourceConfigDto.class::cast)
        .anyMatch(ExternalEventSourceConfigDto::isIncludeAllGroups);
      if (includeAllGroups) {
        return externalEventService;
      } else {
        final List<String> groups = eventImportSourceDto.getEventSourceConfigurations().stream()
          .map(ExternalEventSourceConfigDto.class::cast)
          .map(ExternalEventSourceConfigDto::getGroup)
          .collect(Collectors.toList());
        return new ExternalEventByGroupsFetcherService(groups, externalEventReader);
      }
    } else if (EventSourceType.CAMUNDA.equals(eventImportSourceDto.getEventImportSourceType())) {
      final CamundaEventSourceConfigDto camundaEventSourceConfig =
        (CamundaEventSourceConfigDto) eventImportSourceDto.getEventSourceConfigurations().get(0);
      return new CamundaActivityEventFetcherService(
        camundaEventSourceConfig,
        camundaActivityEventReader,
        timestampBasedImportIndexReader
      );
    } else {
      throw new OptimizeRuntimeException("Cannot find event fetching service for event import source type: "
                                           + eventImportSourceDto.getEventImportSourceType());
    }
  }
}
