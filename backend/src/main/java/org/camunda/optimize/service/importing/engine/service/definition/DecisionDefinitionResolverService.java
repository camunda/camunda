/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.definition;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionResolverService extends AbstractDefinitionResolverService<DecisionDefinitionOptimizeDto> {

  private final DecisionDefinitionReader decisionDefinitionReader;

  @Override
  protected DecisionDefinitionOptimizeDto fetchFromEngine(final String definitionId,
                                                          final EngineContext engineContext) {
    return engineContext.fetchDecisionDefinition(definitionId);
  }

  @Override
  protected void syncCache() {
    decisionDefinitionReader.getAllDecisionDefinitions()
      .forEach(this::addToCacheIfNotNull);
  }

}
