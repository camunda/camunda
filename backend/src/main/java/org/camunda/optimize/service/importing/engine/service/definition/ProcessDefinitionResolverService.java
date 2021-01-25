/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service.definition;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessDefinitionResolverService extends AbstractDefinitionResolverService<ProcessDefinitionOptimizeDto> {

  private final ProcessDefinitionReader processDefinitionReader;

  @Override
  protected ProcessDefinitionOptimizeDto fetchFromEngine(final String definitionId,
                                                         final EngineContext engineContext) {
    return engineContext.fetchProcessDefinition(definitionId);
  }

  @Override
  protected void syncCache() {
    processDefinitionReader.getAllProcessDefinitions()
      .forEach(this::addToCacheIfNotNull);
  }

}
