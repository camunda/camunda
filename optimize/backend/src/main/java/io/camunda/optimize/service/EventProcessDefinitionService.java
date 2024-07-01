/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import io.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import io.camunda.optimize.service.db.reader.EventProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.EventProcessDefinitionWriter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessDefinitionService {

  private final EventProcessDefinitionReader eventProcessDefinitionReader;
  private final EventProcessDefinitionWriter eventProcessDefinitionWriter;

  public void importEventProcessDefinitions(
      final List<EventProcessDefinitionDto> definitionOptimizeDtos) {
    eventProcessDefinitionWriter.importEventProcessDefinitions(definitionOptimizeDtos);
  }

  public List<EventProcessDefinitionDto> getAllEventProcessesDefinitionsOmitXml() {
    return eventProcessDefinitionReader.getAllEventProcessDefinitionsOmitXml();
  }

  public Optional<EventProcessDefinitionDto> getEventProcessDefinitionByKey(
      final String definitionKey) {
    return eventProcessDefinitionReader.getEventProcessDefinitionByKeyOmitXml(definitionKey);
  }

  public void deleteEventProcessDefinitions(final Collection<String> definitionIds) {
    eventProcessDefinitionWriter.deleteEventProcessDefinitions(definitionIds);
  }
}
