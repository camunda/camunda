/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.service.es.reader.EventProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.EventProcessDefinitionWriter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessDefinitionService {

  private final EventProcessDefinitionReader eventProcessDefinitionReader;
  private final EventProcessDefinitionWriter eventProcessDefinitionWriter;

  public void importEventProcessDefinitions(final List<EventProcessDefinitionDto> definitionOptimizeDtos) {
    eventProcessDefinitionWriter.importEventProcessDefinitions(definitionOptimizeDtos);
  }

  public List<EventProcessDefinitionDto> getAllEventProcessesDefinitionsOmitXml() {
    return eventProcessDefinitionReader.getAllEventProcessDefinitionsOmitXml();
  }

  public Optional<EventProcessDefinitionDto> getEventProcessDefinitionByKey(final String definitionKey) {
    return eventProcessDefinitionReader.getEventProcessDefinitionByKeyOmitXml(definitionKey);
  }

  public void deleteEventProcessDefinitions(final Collection<String> definitionIds) {
    eventProcessDefinitionWriter.deleteEventProcessDefinitions(definitionIds);
  }

}
