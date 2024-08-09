/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.definition;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionResolverService
    extends AbstractDefinitionResolverService<DecisionDefinitionOptimizeDto> {

  private final DecisionDefinitionReader decisionDefinitionReader;

  @Override
  protected DecisionDefinitionOptimizeDto fetchFromEngine(
      final String definitionId, final EngineContext engineContext) {
    return engineContext.fetchDecisionDefinition(definitionId);
  }

  @Override
  protected void syncCache() {
    decisionDefinitionReader.getAllDecisionDefinitions().forEach(this::addToCacheIfNotNull);
  }
}
