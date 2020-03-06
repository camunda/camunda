/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess.mediator;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.service.es.reader.BusinessKeyReader;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.reader.TimestampBasedImportIndexReader;
import org.camunda.optimize.service.es.reader.VariableUpdateInstanceReader;
import org.camunda.optimize.service.events.CustomTracedCamundaEventFetcherService;
import org.camunda.optimize.service.events.EventFetcherService;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

@RequiredArgsConstructor
@Component
public class EventFetcherFactory {
  private final ConfigurationService configurationService;

  private final ExternalEventService externalEventService;

  private final CamundaActivityEventReader camundaActivityEventReader;
  private final ProcessDefinitionReader processDefinitionReader;
  private final VariableUpdateInstanceReader variableUpdateInstanceReader;
  private final BusinessKeyReader businessKeyReader;
  private final TimestampBasedImportIndexReader timestampBasedImportIndexReader;

  public EventFetcherService createEventFetcherForEventSource(EventSourceEntryDto eventSourceEntryDto) {
    switch (eventSourceEntryDto.getType()) {
      case EXTERNAL:
        return externalEventService;
      case CAMUNDA:
        return new CustomTracedCamundaEventFetcherService(
          eventSourceEntryDto.getProcessDefinitionKey(),
          eventSourceEntryDto,
          new SimpleDateFormat(configurationService.getEngineDateFormat()),
          camundaActivityEventReader,
          processDefinitionReader,
          variableUpdateInstanceReader,
          businessKeyReader,
          timestampBasedImportIndexReader
        );
      default:
        throw new OptimizeRuntimeException("Cannot find event fetching service for event import source type: "
                                             + eventSourceEntryDto.getType());
    }
  }
}
