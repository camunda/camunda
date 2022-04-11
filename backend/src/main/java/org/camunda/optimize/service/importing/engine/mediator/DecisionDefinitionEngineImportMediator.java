/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.definition.DecisionDefinitionFetcher;
import org.camunda.optimize.service.importing.engine.handler.DecisionDefinitionImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionEngineImportMediator
  extends TimestampBasedImportMediator<DecisionDefinitionImportIndexHandler, DecisionDefinitionEngineDto> {

  private final DecisionDefinitionFetcher engineEntityFetcher;


  public DecisionDefinitionEngineImportMediator(final DecisionDefinitionImportIndexHandler importIndexHandler,
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
    return engineEntityFetcher.fetchDefinitionsForTimestamp(importIndexHandler.getTimestampOfLastEntity());
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
