/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator;

import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import io.camunda.optimize.service.importing.TimestampBasedImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.definition.DecisionDefinitionFetcher;
import io.camunda.optimize.service.importing.engine.handler.DecisionDefinitionImportIndexHandler;
import io.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionEngineImportMediator
    extends TimestampBasedImportMediator<
        DecisionDefinitionImportIndexHandler, DecisionDefinitionEngineDto> {

  private final DecisionDefinitionFetcher engineEntityFetcher;

  public DecisionDefinitionEngineImportMediator(
      final DecisionDefinitionImportIndexHandler importIndexHandler,
      final DecisionDefinitionFetcher engineEntityFetcher,
      final DecisionDefinitionImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  protected OffsetDateTime getTimestamp(final DecisionDefinitionEngineDto definitionEngineDto) {
    return definitionEngineDto.getDeploymentTime();
  }

  @Override
  protected List<DecisionDefinitionEngineDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchDefinitions(importIndexHandler.getNextPage());
  }

  @Override
  protected List<DecisionDefinitionEngineDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchDefinitionsForTimestamp(
        importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportDecisionDefinitionMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.DEFINITION;
  }
}
