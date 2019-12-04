/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.service.es.reader.EventProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.EventProcessDefinitionWriter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessDefinitionService {

  private final EventProcessDefinitionReader eventProcessDefinitionReader;
  private final EventProcessDefinitionWriter eventProcessDefinitionWriter;

  public Optional<EventProcessDefinitionDto> getEventProcessDefinition(final String eventProcessDefinitionId) {
    return eventProcessDefinitionReader.getEventProcessDefinition(eventProcessDefinitionId);
  }

  public void createEventProcessDefinition(final EventProcessDefinitionDto definitionDto) {
    eventProcessDefinitionWriter.createEventProcessDefinition(definitionDto);
  }

  public List<EventProcessDefinitionDto> getAllEventProcessesDefinitionsOmitXml() {
    return eventProcessDefinitionReader.getAllPublishedEventProcessesOmitXml();
  }

  public boolean deleteEventProcessDefinition(final String eventProcessDefinitionId) {
    return eventProcessDefinitionWriter.deleteEventProcessDefinition(eventProcessDefinitionId);
  }

}
