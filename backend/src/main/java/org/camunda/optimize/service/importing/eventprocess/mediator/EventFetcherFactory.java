/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess.mediator;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.process.EventSourceEntryDto;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.es.reader.TimestampBasedImportIndexReader;
import org.camunda.optimize.service.events.CamundaActivityEventFetcherService;
import org.camunda.optimize.service.events.EventFetcherService;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EventFetcherFactory {

  private final ExternalEventService externalEventService;

  private final CamundaActivityEventReader camundaActivityEventReader;
  private final TimestampBasedImportIndexReader timestampBasedImportIndexReader;

  public EventFetcherService createEventFetcherForEventSource(EventSourceEntryDto eventSourceEntryDto) {
    switch (eventSourceEntryDto.getType()) {
      case EXTERNAL:
        return externalEventService;
      case CAMUNDA:
        return new CamundaActivityEventFetcherService(
          eventSourceEntryDto.getProcessDefinitionKey(),
          eventSourceEntryDto,
          camundaActivityEventReader,
          timestampBasedImportIndexReader
        );
      default:
        throw new OptimizeRuntimeException("Cannot find event fetching service for event import source type: "
                                             + eventSourceEntryDto.getType());
    }
  }
}
